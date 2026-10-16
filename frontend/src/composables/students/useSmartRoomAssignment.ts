import { ElMessage, ElMessageBox } from 'element-plus'
import { escapeHtml } from '../../utils/html.js'
import { roomApi } from '../../api/room'

type StudentLike = {
  id: number
  name: string
  className?: string
  gender?: string
}

type RoomLike = {
  id: number
  building: { name: string; type?: string }
  floor?: number
  roomNumber: string
  capacity: number
  occupied: number
  status?: string
}

type StudentGroup = {
  className: string
  gender: string
  students: StudentLike[]
}

export function groupStudentsByClassAndGender(students: StudentLike[]) {
  const groups: Record<string, StudentGroup> = {}

  students.forEach((student) => {
    const key = `${student.className || '未分班'}_${student.gender}`
    if (!groups[key]) {
      groups[key] = {
        className: student.className || '未分班',
        gender: student.gender || '',
        students: []
      }
    }
    groups[key].students.push(student)
  })

  return Object.values(groups).sort((a, b) => b.students.length - a.students.length)
}

export function groupRoomsByBuilding(rooms: RoomLike[]) {
  const roomsByBuilding: Record<string, RoomLike[]> = {}

  rooms.forEach((room) => {
    const buildingName = room.building.name
    if (!roomsByBuilding[buildingName]) {
      roomsByBuilding[buildingName] = []
    }
    roomsByBuilding[buildingName].push(room)
  })

  Object.keys(roomsByBuilding).forEach((building) => {
    roomsByBuilding[building].sort((a, b) => {
      if ((a.floor ?? 0) !== (b.floor ?? 0)) return (a.floor ?? 0) - (b.floor ?? 0)
      return a.roomNumber.localeCompare(b.roomNumber)
    })
  })

  return roomsByBuilding
}

async function assignRoomsForGroup(group: StudentGroup, roomsByBuilding: Record<string, RoomLike[]>) {
  let success = 0
  let failed = 0
  const messages: string[] = []

  let bestBuilding = ''
  let maxAvailableBeds = 0

  Object.keys(roomsByBuilding).forEach((building) => {
    const availableBeds = roomsByBuilding[building].reduce(
      (sum, room) => sum + (room.capacity - room.occupied),
      0
    )
    if (availableBeds > maxAvailableBeds) {
      maxAvailableBeds = availableBeds
      bestBuilding = building
    }
  })

  const genderText = group.gender === 'MALE' ? '男' : '女'

  if (!bestBuilding) {
    messages.push(`❌ ${group.className} (${genderText}): 无可用楼栋`)
    return { success, failed: group.students.length, messages }
  }

  const selectedRooms = roomsByBuilding[bestBuilding]
  let roomIndex = 0

  messages.push(`🏠 ${group.className} (${genderText}) → ${bestBuilding}`)

  for (const student of group.students) {
    try {
      while (
        roomIndex < selectedRooms.length &&
        selectedRooms[roomIndex].occupied >= selectedRooms[roomIndex].capacity
      ) {
        roomIndex++
      }

      if (roomIndex >= selectedRooms.length) {
        messages.push(`   ❌ ${student.name}: 该楼栋已满`)
        failed++
        continue
      }

      const room = selectedRooms[roomIndex]
      await roomApi.assignStudent(student.id, room.id)
      room.occupied++

      messages.push(`   ✅ ${student.name} → ${room.roomNumber}`)
      success++
    } catch {
      messages.push(`   ❌ ${student.name}: 分配失败`)
      failed++
    }
  }

  return { success, failed, messages }
}

function createAssignmentResultHtml(successCount: number, failCount: number, results: string[]) {
  const statsHtml = `
    <div class="result-stats">
      <div class="stat-item success">
        <div class="stat-number">${successCount}</div>
        <div class="stat-label">成功入住</div>
      </div>
      ${
        failCount > 0
          ? `
      <div class="stat-item failed">
        <div class="stat-number">${failCount}</div>
        <div class="stat-label">分配失败</div>
      </div>`
          : ''
      }
    </div>
  `

  let detailsHtml = '<div class="result-details">'

  results.forEach((line, index) => {
    const nextLine = results[index + 1]
    const closesGroup = nextLine?.startsWith('🏠') || index === results.length - 1

    if (line.startsWith('🏠')) {
      const buildingInfo = line.replace('🏠 ', '').split(' → ')
      detailsHtml += `
        <div class="building-group">
          <div class="building-header">
            <div class="building-icon">🏠</div>
            <div class="building-info">
              <div class="class-name">${escapeHtml(buildingInfo[0])}</div>
              <div class="building-name">${escapeHtml(buildingInfo[1])}</div>
            </div>
          </div>
          <div class="students-list">
      `
    } else if (line.startsWith('   ✅')) {
      const studentInfo = line.replace('   ✅ ', '').split(' → ')
      detailsHtml += `
        <div class="student-item success">
          <div class="student-avatar">
            <div class="avatar-circle success">✓</div>
          </div>
          <div class="student-info">
            <div class="student-name">${escapeHtml(studentInfo[0])}</div>
            <div class="room-number">${escapeHtml(studentInfo[1])}</div>
          </div>
        </div>
      `
    } else if (line.startsWith('   ❌')) {
      const studentInfo = line.replace('   ❌ ', '')
      detailsHtml += `
        <div class="student-item failed">
          <div class="student-avatar">
            <div class="avatar-circle failed">×</div>
          </div>
          <div class="student-info">
            <div class="student-name">${escapeHtml(studentInfo.split(':')[0])}</div>
            <div class="error-reason">${escapeHtml(studentInfo.split(':')[1] || '分配失败')}</div>
          </div>
        </div>
      `
    }

    if (closesGroup && line.startsWith('🏠')) {
      detailsHtml += '</div></div>'
    }
  })

  detailsHtml += '</div>'
  return `<div class="apple-assignment-result">${statsHtml}${detailsHtml}</div>`
}

export async function performSmartRoomAssignment(students: StudentLike[]) {
  try {
    ElMessage.info('正在智能分配宿舍，请稍候...')

    const groupedStudents = groupStudentsByClassAndGender(students)
    const allRooms = await roomApi.getAvailable()

    let successCount = 0
    let failCount = 0
    const assignmentResults: string[] = []

    for (const group of groupedStudents) {
      const availableRooms = allRooms.filter(
        (room) =>
          room.building.type === group.gender &&
          room.status === 'AVAILABLE' &&
          room.occupied < room.capacity
      )

      if (availableRooms.length === 0) {
        failCount += group.students.length
        assignmentResults.push(
          `❌ ${group.className || '未分班'} (${group.gender === 'MALE' ? '男' : '女'}): 无可用宿舍`
        )
        continue
      }

      const roomsByBuilding = groupRoomsByBuilding(availableRooms as RoomLike[])
      const result = await assignRoomsForGroup(group, roomsByBuilding)
      successCount += result.success
      failCount += result.failed
      assignmentResults.push(...result.messages)
    }

    showAssignmentResults(successCount, failCount, assignmentResults)
    return { successCount, failCount }
  } catch (error) {
    console.error('智能分配失败:', error)
    ElMessage.error('智能分配过程中出现错误')
    return { successCount: 0, failCount: students.length }
  }
}

export function showAssignmentResults(successCount: number, failCount: number, results: string[]) {
  void ElMessageBox({
    title: '🎉 智能分配完成',
    message: createAssignmentResultHtml(successCount, failCount, results),
    showCancelButton: false,
    confirmButtonText: '完成',
    customClass: 'apple-result-dialog',
    dangerouslyUseHTMLString: true,
    center: true
  }).catch(() => {})

  if (successCount > 0) {
    ElMessage.success(
      `智能分配完成！成功入住 ${successCount} 人${failCount > 0 ? `，${failCount} 人分配失败` : ''}`
    )
  } else {
    ElMessage.error('批量入住失败，请检查宿舍资源')
  }
}
