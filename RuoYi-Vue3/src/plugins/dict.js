import { getDicts } from '@/api/system/dict/data'
import useDictStore from '@/store/modules/dict'

/**
 * 为 Options API 组件提供 Vue2 RuoYi 风格的 dict.type 字典支持。
 * 迁移自 Vue2 的 components/DictData/index.js（DictData.install 全局 mixin）。
 * 用法：组件声明 dicts: ['biz_yes_no']，模板中 dict.type.biz_yes_no。
 */
function normalize(items) {
  return (items || []).map(p => {
    if (p && 'label' in p && 'value' in p) {
      return p
    }
    return {
      label: p.dictLabel,
      value: p.dictValue,
      elTagType: p.listClass,
      elTagClass: p.cssClass
    }
  })
}

export default function installDictMixin(app) {
  app.mixin({
    data() {
      const dicts = this.$options.dicts
      if (!Array.isArray(dicts) || !dicts.length) {
        return {}
      }
      return { dict: { type: {} } }
    },
    created() {
      const dicts = this.$options.dicts
      if (!Array.isArray(dicts) || !dicts.length) {
        return
      }
      const store = useDictStore()
      dicts.forEach(type => {
        if (!type) {
          return
        }
        const cached = store.getDict(type)
        if (cached) {
          this.dict.type[type] = normalize(cached)
        } else {
          getDicts(type).then(resp => {
            const mapped = normalize(resp.data)
            this.dict.type[type] = mapped
            store.setDict(type, mapped)
          })
        }
      })
    }
  })
}
