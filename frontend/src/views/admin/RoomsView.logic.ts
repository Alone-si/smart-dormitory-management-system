import { escapeHtml } from '../../utils/html.js'
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Home, Users, AlertCircle } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roomApi } from '../../api/room'
import request from '../../utils/request'
import type { Room } from '../../types'

export function useRoomsView() {
const router = useRouter()

const loading = ref(false)
const rooms = ref<Room[]>([])
const currentPage = ref(1)
const pageSize = ref(21)

// 筛选条件
const genderFilter = ref('')
const buildingFilter = ref('')
const classFilter = ref('')

// 获取唯一的楼栋列表
const uniqueBuildings = computed(() => {
  const buildings = new Set(rooms.value.map(room => room.building.name))
  return Array.from(buildings).sort()
})

// 获取唯一的班级列表
const uniqueClasses = computed(() => {
  const classes = new Set<string>()
  rooms.value.forEach(room => {
    if (room.students && room.students.length > 0) {
      room.students.forEach(student => {
        if (student.className) {
          classes.add(student.className)
        }
      })
    }
  })
  return Array.from(classes).sort()
})

// 检查是否有激活的筛选器
const hasActiveFilters = computed(() => {
  return genderFilter.value || buildingFilter.value || classFilter.value
})

// 重置筛选
const resetFilters = () => {
  genderFilter.value = ''
  buildingFilter.value = ''
  classFilter.value = ''
  currentPage.value = 1
}

// 筛选后的宿舍列表
const filteredRooms = computed(() => {
  let filtered = rooms.value

  // 性别筛选
  if (genderFilter.value) {
    filtered = filtered.filter(room => room.building.type === genderFilter.value)
  }

  // 楼栋筛选
  if (buildingFilter.value) {
    filtered = filtered.filter(room => room.building.name === buildingFilter.value)
  }

  // 班级筛选
  if (classFilter.value) {
    filtered = filtered.filter(room => {
      if (!room.students || room.students.length === 0) return false
      return room.students.some(student => student.className === classFilter.value)
    })
  }

  return filtered
})

const paginatedRooms = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredRooms.value.slice(start, end)
})

const totalOccupied = computed(() => {
  return rooms.value.reduce((total, room) => total + room.occupied, 0)
})

const handlePageChange = (page: number) => {
  currentPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const handlePageSizeChange = () => {
  currentPage.value = 1
}

const loadRooms = async () => {
  loading.value = true
  try {
    const data: Room[] = await request.get('/rooms/with-students')
    rooms.value = data
  } catch (error) {
    ElMessage.error('加载房间列表失败')
  } finally {
    loading.value = false
  }
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    AVAILABLE: 'success',
    FULL: 'warning',
    MAINTENANCE: 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    AVAILABLE: '可入住',
    FULL: '已满',
    MAINTENANCE: '维护中'
  }
  return map[status] || status
}

const getProgressColor = (occupied: number, capacity: number) => {
  const rate = occupied / capacity
  if (rate < 0.5) return '#67c23a'
  if (rate < 0.8) return '#e6a23c'
  return '#f56c6c'
}

// 查看学生详情
const viewStudentDetail = (student: any) => {
  // 生成头像显示内容
  const getAvatarContent = () => {
    if (student.avatar) {
      // 如果有自定义头像，显示图片
      return `<img src="${escapeHtml(student.avatar)}" alt="${escapeHtml(student.name)}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 50%;" />`
    } else {
      // 没有头像则显示姓名首字母
      const firstChar = student.name ? student.name.charAt(0) : '?'
      return `<div style="width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 600; color: white;">${escapeHtml(firstChar)}</div>`
    }
  }

  ElMessageBox({
    title: '',
    message: `
      <div class="simple-student-detail">
        <div class="student-header">
          <div class="student-avatar">
            ${getAvatarContent()}
          </div>
          <div class="student-info">
            <h3>${escapeHtml(student.name)}</h3>
            <p>学号：${escapeHtml(student.studentId)}</p>
          </div>
        </div>
        
        <div class="student-details">
          <div class="detail-row">
            <span class="label">班级</span>
            <span class="value">${escapeHtml(student.className || '未分班')}</span>
          </div>
          <div class="detail-row">
            <span class="label">性别</span>
            <span class="value">${student.gender === 'MALE' ? '男' : '女'}</span>
          </div>
          ${student.phone ? `
          <div class="detail-row">
            <span class="label">电话</span>
            <span class="value">${escapeHtml(student.phone)}</span>
          </div>
          ` : ''}
        </div>
      </div>
    `,
    showCancelButton: false,
    confirmButtonText: '关闭',
    dangerouslyUseHTMLString: true,
    customClass: 'simple-student-dialog',
    center: true
  })
}

// 为宿舍添加学生
const addStudentToRoom = (room: any) => {
  ElMessageBox({
    title: '',
    message: `
      <div class="simple-add-student-dialog">
        <div class="dialog-header">
          <div class="header-icon">🏠</div>
          <div class="header-content">
            <div class="room-title">${escapeHtml(room.building.name)} - ${escapeHtml(room.roomNumber)}</div>
            <div class="room-subtitle">为此宿舍添加学生</div>
          </div>
        </div>
        
        <div class="room-stats">
          <div class="stat-item">
            <div class="stat-icon">🏠</div>
            <div class="stat-number">${escapeHtml(room.capacity)}</div>
            <div class="stat-label">房间容量</div>
          </div>
          <div class="stat-item">
            <div class="stat-icon">👥</div>
            <div class="stat-number">${escapeHtml(room.occupied)}</div>
            <div class="stat-label">当前入住</div>
          </div>
          <div class="stat-item available">
            <div class="stat-icon">✨</div>
            <div class="stat-number">${room.capacity - room.occupied}</div>
            <div class="stat-label">剩余床位</div>
          </div>
        </div>
        
        <div class="action-guide">
          <div class="guide-icon">💡</div>
          <div class="guide-text">
            请前往学生管理页面进行批量入住操作<br>
            或在学生详情页面为单个学生分配宿舍
          </div>
        </div>
      </div>
    `,
    showCancelButton: true,
    confirmButtonText: '前往学生管理',
    cancelButtonText: '取消',
    dangerouslyUseHTMLString: true,
    customClass: 'simple-add-student-dialog-box',
    center: true
  }).then(() => {
    // 跳转到学生管理页面
    router.push('/admin/students')
  }).catch(() => {
    // 用户取消，不做任何操作
  })
}

onMounted(() => {
  loadRooms()
})
  return {
  addStudentToRoom,
  AlertCircle,
  buildingFilter,
  classFilter,
  currentPage,
  filteredRooms,
  genderFilter,
  getProgressColor,
  getStatusText,
  getStatusType,
  handlePageChange,
  handlePageSizeChange,
  hasActiveFilters,
  Home,
  loading,
  pageSize,
  paginatedRooms,
  resetFilters,
  rooms,
  uniqueBuildings,
  uniqueClasses,
  Users,
  viewStudentDetail
  }
}