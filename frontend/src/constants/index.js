/**
 * 全局常量与枚举字典。
 *
 * 说明：所有 id 在后端都以字符串下发（Long → String），前端一律按 string 处理。
 */

/** 后端统一响应码 */
export const RESULT_CODE = {
  SUCCESS: 200,
  PARAM_ERROR: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  METHOD_NOT_ALLOWED: 405,
  TOO_MANY_REQUESTS: 429,
  BIZ_ERROR: 500,
  SYSTEM_ERROR: 500
}

/** 分页默认值（与后端 currentPage / pageSize 对齐） */
export const DEFAULT_PAGE = {
  currentPage: 1,
  pageSize: 12
}

/** 私信分页默认值（后端默认 20） */
export const CHAT_PAGE = {
  currentPage: 1,
  pageSize: 20
}

/** 弹窗模式 */
export const DIALOG_MODE = {
  CREATE: 'create',
  EDIT: 'edit'
}

/** 金额格式（0.01 ~ 99999.99，最多两位小数） */
export const PRICE_PATTERN = /^(?!0+(\.0{1,2})?$)\d{1,5}(\.\d{1,2})?$/

/**
 * 手机号：空 或 11 位大陆手机号（1 开头，第二位 3-9）。
 *
 * <p>与后端 `ValidationPatterns.PHONE` 保持完全一致，改一处必须同步另一处；
 * 只校验位数是不够的——00000000000 这类值会一路带进库。</p>
 */
export const PHONE_PATTERN = /^$|^1[3-9]\d{9}$/

/** 手机号校验失败提示（与 PHONE_PATTERN 同口径） */
export const PHONE_MESSAGE = '手机号格式不正确，应为 11 位大陆手机号'

/**
 * 过滤非数字字符（只保留 0-9，并截断到 11 位）。
 *
 * <p>`maxlength` 只能限制长度、拦不住字母，输入框需要配合本函数使用。</p>
 *
 * @param {string} value 输入框原始值
 * @returns {string} 纯数字手机号
 */
export function sanitizePhone(value) {
  return String(value ?? '').replace(/\D/g, '').slice(0, 11)
}

/** 角色字典 */
export const ROLE_DICT = {
  USER: { label: '普通用户', color: '' }
}

/** 用户状态字典 */
export const USER_STATUS_DICT = {
  NORMAL: { label: '正常', color: 'success' },
  DISABLED: { label: '已禁用', color: 'info' }
}

/** 商品状态字典（5 种） */
export const ITEM_STATUS_DICT = {
  PENDING: { label: '待审核', color: 'warning' },
  ON_SALE: { label: '在售', color: 'success' },
  OFF_SHELF: { label: '已下架', color: 'info' },
  SOLD: { label: '已售出', color: 'danger' },
  REJECTED: { label: '审核拒绝', color: 'danger' }
}

/** 成色字典 */
export const CONDITION_DICT = {
  BRAND_NEW: { label: '全新', color: 'success' },
  LIKE_NEW: { label: '几乎全新', color: 'success' },
  LIGHT_USE: { label: '轻微使用', color: 'warning' },
  HEAVY_USE: { label: '明显使用', color: 'info' }
}

/** 交易方式字典 */
export const TRADE_TYPE_DICT = {
  MEET: { label: '面交', color: 'success' },
  SHIP: { label: '邮寄', color: 'warning' },
  BOTH: { label: '面交或邮寄', color: '' }
}

/** 订单状态字典（6 种） */
export const ORDER_STATUS_DICT = {
  UNPAID: { label: '待付款', color: 'warning' },
  TO_SHIP: { label: '待发货', color: '' },
  TO_RECEIVE: { label: '待收货', color: '' },
  TO_MEET: { label: '待面交', color: 'success' },
  DONE: { label: '已完成', color: 'info' },
  CANCELED: { label: '已取消', color: 'info' }
}

/** 支付方式字典 */
export const PAY_METHOD_DICT = {
  WECHAT: { label: '微信支付', icon: 'ChatDotRound' },
  ALIPAY: { label: '支付宝', icon: 'Wallet' },
  BANK: { label: '银行卡', icon: 'CreditCard' }
}

/** 举报原因字典 */
export const REPORT_REASON_DICT = {
  FAKE_INFO: '虚假信息',
  CONTRABAND: '违禁物品',
  PRICE_FRAUD: '价格欺诈',
  STOLEN_IMAGE: '盗图',
  OTHER: '其他'
}

/** 举报状态字典 */
export const REPORT_STATUS_DICT = {
  PENDING: { label: '待处理', color: 'warning' },
  HANDLED: { label: '已处理', color: 'success' }
}

/** 举报处理动作字典 */
export const HANDLE_ACTION_DICT = {
  OFFLINE: '下架商品',
  REJECT: '驳回举报'
}

/** 订单关闭原因字典 */
export const CLOSE_REASON_DICT = {
  TIMEOUT: '超时未支付',
  BUYER_CANCEL: '买家取消',
  SELLER_CANCEL: '卖家取消',
  SYSTEM: '系统关闭'
}

/** 商品排序选项 */
export const SORT_OPTIONS = [
  { value: 'latest', label: '最新发布' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
  { value: 'hot', label: '最多浏览' }
]

/** 商品图片上限 */
export const MAX_ITEM_IMAGES = 9

/**
 * 品牌快捷标签：子类名 → 常见品牌列表。
 *
 * <p>发布页选择子类后展示对应标签，点击一键填入「品牌型号」；无匹配的子类不展示。</p>
 */
export const BRAND_TAGS_BY_CATEGORY = {
  '手机/平板': ['iPhone', '华为', '小米', 'iPad', '红米', '荣耀', 'OPPO', 'vivo'],
  '耳机/音箱': ['AirPods', '索尼', 'Bose', 'JBL', '小米', '漫步者'],
  '相机/无人机': ['佳能', '索尼', '尼康', '富士', '大疆'],
  '智能手表/手环': ['Apple Watch', '小米手环', '华为手环', '华米'],
  '游戏设备': ['Switch', 'PS5', 'Xbox', 'Steam Deck'],
  '数码配件': ['倍思', '绿联', 'Anker', '紫米'],
  笔记本: ['联想', '华硕', '戴尔', 'MacBook', '惠普', '机械革命'],
  '台式机/一体机': ['联想', '戴尔', '惠普', 'iMac'],
  显示器: ['戴尔', 'AOC', '三星', 'LG', '红米'],
  键鼠外设: ['罗技', '雷蛇', 'HHKB', 'Cherry', '达尔优'],
  '打印机/办公设备': ['惠普', '佳能', '兄弟', '爱普生'],
  小家电: ['美的', '苏泊尔', '九阳', '米家', '小米'],
  '家具/床品': ['宜家', '网易严选', '源氏木语'],
  厨房用品: ['苏泊尔', '九阳', '美的'],
  '收纳/清洁': ['宜家', '妙洁', '美丽雅'],
  球类运动: ['斯伯丁', '李宁', '尤尼克斯'],
  健身器材: ['Keep', '迪卡侬', '舒华'],
  户外装备: ['骆驼', '迪卡侬', '探路者', '牧高笛'],
  '骑行/球鞋': ['捷安特', '美利达', '耐克', '阿迪达斯', '李宁'],
  鞋靴: ['耐克', '阿迪达斯', '新百伦', '李宁'],
  箱包: ['新秀丽', '小米', '90分'],
  '电子书/阅读器': ['Kindle', '掌阅', '文石'],
  '存储/硬盘': ['闪迪', '三星', '西数', '金士顿'],
  网络设备: ['小米', 'TP-LINK', '华硕']
}

/** 评价图片上限 */
export const MAX_REVIEW_IMAGES = 3

/** 待付款超时（分钟），与后端 brix.order.unpaid-timeout-minutes 一致 */
export const UNPAID_TIMEOUT_MINUTES = 30
