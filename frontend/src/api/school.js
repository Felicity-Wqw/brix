import request from '@/utils/request'

/**
 * 学校 / 校区接口。
 *
 * <p>数据量固定（桂林 13 校 20 校区，约 4KB），前端缓存 7 天：
 * 首页筛选、发布页、注册页、个人资料页共用同一份缓存，避免重复请求。
 * 后台调整校区后，改一次 CACHE_KEY 版本号即可让所有客户端失效。</p>
 */

const CACHE_KEY = 'brix_schools_v1'
const CACHE_TTL = 7 * 24 * 60 * 60 * 1000

/**
 * 拉取学校列表（含各自校区）。
 *
 * @param {boolean} [force] 是否跳过缓存强制刷新
 * @returns {Promise<Array>} 学校数组，元素含 campuses 子数组
 */
export async function loadSchools(force = false) {
  if (!force) {
    try {
      const raw = localStorage.getItem(CACHE_KEY)
      if (raw) {
        const cached = JSON.parse(raw)
        if (Array.isArray(cached?.data) && Date.now() - cached.at < CACHE_TTL) {
          return cached.data
        }
      }
    } catch (error) {
      // 缓存损坏时忽略，继续走网络请求
    }
  }

  const data = await request.get('/schools')
  const list = Array.isArray(data) ? data : []
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ at: Date.now(), data: list }))
  } catch (error) {
    // 隐私模式或配额不足时忽略写缓存失败，不影响功能
  }
  return list
}

/**
 * 在已加载的学校列表中查找学校。
 *
 * @param {Array} schools 学校列表
 * @param {string|number} schoolId 学校 ID（后端 Long → 字符串）
 * @returns {object|undefined}
 */
export function findSchool(schools, schoolId) {
  if (!schoolId) return undefined
  return (schools || []).find((s) => String(s.id) === String(schoolId))
}

/**
 * 在已加载的学校列表中查找校区。
 *
 * @param {Array} schools 学校列表
 * @param {string|number} campusId 校区 ID
 * @returns {object|undefined}
 */
export function findCampus(schools, campusId) {
  if (!campusId) return undefined
  for (const school of schools || []) {
    const hit = (school.campuses || []).find((c) => String(c.id) === String(campusId))
    if (hit) return hit
  }
  return undefined
}

/**
 * 按校区名称反查归属（学校 ID + 校区 ID）。
 *
 * <p>「雁山校区」这类名称会被多所学校共用（桂林 5 所高校都有），
 * 因此只有全库唯一匹配时才返回结果；重名一律返回 null 交由用户明确选择——
 * 猜错会把商品/用户挂到别的学校名下，直接污染按学校统计的口径。</p>
 *
 * @param {Array} schools 学校列表
 * @param {string} name 校区名称
 * @returns {{schoolId: string, campusId: string, schoolName: string}|null}
 */
export function resolveCampusByName(schools, name) {
  if (!name) return null
  const hits = []
  for (const school of schools || []) {
    for (const campus of school.campuses || []) {
      if (campus.name === name) {
        hits.push({ schoolId: school.id, campusId: campus.id, schoolName: school.name })
      }
    }
  }
  return hits.length === 1 ? hits[0] : null
}
