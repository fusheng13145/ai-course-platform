import demo from './demo.json'
import type { V2Module, V2Role } from '../nav'

const roleModules = demo.db.roles as unknown as Record<V2Role, { modules: V2Module[] }>

export function roleModuleList(role: V2Role): V2Module[] {
  return roleModules[role]?.modules || []
}

export function findModule(role: V2Role, id: string): V2Module | undefined {
  return roleModuleList(role).find((item) => item.id === id)
}
