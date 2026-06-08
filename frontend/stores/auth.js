import { defineStore } from "pinia"
import { ref, computed } from "vue"
import authApi from "@/api/auth"

const mockUsers = {
  admin: { id: 1, username: "admin", fullName: "管理员", role: "ADMIN", email: "admin@example.com" },
  teacher: { id: 2, username: "teacher", fullName: "李老师", role: "TEACHER", email: "teacher@example.com" },
  student: { id: 3, username: "student", fullName: "张三", role: "STUDENT", email: "student@example.com" }
}
const mockPermissions = {
  ADMIN: { menus: ["dashboard","users","courses","analytics","exams","grades","questions","notifications","permissions","system"], buttons: { users: ["users:create","users:edit","users:delete"] } },
  TEACHER: { menus: ["dashboard","courses","analytics","exams","grades"], buttons: { courses: ["courses:create","courses:edit"] } },
  STUDENT: { menus: ["dashboard","courses","knowledge-graph","learning-path","ai-assistant","exams","grades","wrong-questions","profile"], buttons: {} }
}

function cleanStorage() {
  ["token","user","permissions","isMockLogin"].forEach(k => { localStorage.removeItem(k); sessionStorage.removeItem(k) })
}

export const useAuthStore = defineStore("auth", () => {
  const token = ref(localStorage.getItem("token") || sessionStorage.getItem("token") || "")
  const user = ref(JSON.parse(localStorage.getItem("user") || sessionStorage.getItem("user") || "null"))
  const permissions = ref(JSON.parse(localStorage.getItem("permissions") || sessionStorage.getItem("permissions") || "null"))
  const isLoading = ref(false)
  const isMockLogin = ref(localStorage.getItem("isMockLogin") === "true" || sessionStorage.getItem("isMockLogin") === "true")

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const userId = computed(() => user.value?.id || null)
  const userRole = computed(() => user.value?.role || "")
  const userName = computed(() => user.value?.fullName || user.value?.username || "")

  function save(persistent) {
    const s = persistent ? localStorage : sessionStorage
    s.setItem("token", token.value)
    s.setItem("user", JSON.stringify(user.value))
    s.setItem("isMockLogin", String(isMockLogin.value))
    if (permissions.value) s.setItem("permissions", JSON.stringify(permissions.value))
  }

  async function login(credentials) {
    try {
      isLoading.value = true
      const remember = credentials.remember !== false
      try {
        const res = await authApi.login({ username: credentials.username, password: credentials.password, rememberMe: remember })
        if (res.token) { token.value = res.token; user.value = res.user; isMockLogin.value = false }
        else throw new Error("Login failed")
      } catch (e) {
        console.warn("API login failed, using mock:", e)
        const mu = mockUsers[credentials.username?.toLowerCase()] || mockUsers.student
        token.value = "mock-token-" + Date.now(); user.value = mu; isMockLogin.value = true
        permissions.value = mockPermissions[mu.role] || mockPermissions.STUDENT
      }
      cleanStorage(); save(remember)
      if (permissions.value) await fetchPermissions(remember)
      return { success: true, user: user.value }
    } finally { isLoading.value = false }
  }

  async function fetchPermissions(persistent) {
    try { const res = await authApi.getPermissions(); permissions.value = res; const s = persistent!==false ? localStorage : sessionStorage; s.setItem("permissions", JSON.stringify(res)) }
    catch { if (user.value?.role && mockPermissions[user.value.role]) { permissions.value = mockPermissions[user.value.role]; const s = persistent!==false ? localStorage : sessionStorage; s.setItem("permissions", JSON.stringify(permissions.value)) } }
  }

  async function register(data) {
    isLoading.value = true
    try { const res = await authApi.register(data); return { success: true, data: res } }
    finally { isLoading.value = false }
  }

  async function logout() {
    try { if (token.value && !isMockLogin.value) await authApi.logout() } catch {}
    finally { token.value = ""; user.value = null; permissions.value = null; isMockLogin.value = false; cleanStorage() }
  }

  return { token, user, permissions, isLoading, isMockLogin, isLoggedIn, userId, userRole, userName, login, register, logout, fetchPermissions }
})
