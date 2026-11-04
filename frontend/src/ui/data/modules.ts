import demo from './demo.json'
import type { Module, Role } from '../nav'

const roleModules = demo.db.roles as unknown as Record<Role, { modules: Module[] }>

export function roleModuleList(role: Role): Module[] {
  return roleModules[role]?.modules || []
}

export function findModule(role: Role, id: string): Module | undefined {
  return roleModuleList(role).find((item) => item.id === id)
}
