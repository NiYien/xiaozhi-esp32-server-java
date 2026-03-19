export interface Firmware {
  firmwareId: number
  firmwareName: string
  version: string
  chipModelName: string | null
  deviceType: string | null
  url: string
  fileSize: number | null
  fileHash: string | null
  description: string | null
  isDefault: '0' | '1'
  userId: number | null
  createTime: string
  updateTime: string
}

export interface FirmwareQueryParams {
  firmwareName?: string
  chipModelName?: string
  version?: string
  start?: number
  limit?: number
}
