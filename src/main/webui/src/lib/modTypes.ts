// Display names of HeadlessMc's mod types.
const NAMES: Record<string, { plural: string; singular: string }> = {
  mod: { plural: 'Mods', singular: 'mod' },
  plugin: { plural: 'Plugins', singular: 'plugin' },
  resourcepack: { plural: 'Resource packs', singular: 'resource pack' },
  shader: { plural: 'Shader packs', singular: 'shader pack' },
  datapack: { plural: 'Data packs', singular: 'data pack' },
  modpack: { plural: 'Mod packs', singular: 'mod pack' },
}

export function typeName(type: string, plural = true): string {
  const names = NAMES[type]
  return names ? (plural ? names.plural : names.singular) : type
}

/** The files a mod type accepts, for file pickers and drag and drop. */
export function acceptsFile(name: string): boolean {
  return /\.(jar|zip)$/i.test(name)
}
