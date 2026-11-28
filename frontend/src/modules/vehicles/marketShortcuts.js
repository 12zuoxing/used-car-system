/**
 * 首页入口和市场快捷筛选共用查询条件，打开的结果可以复制链接或返回。
 * @author pangzhihao
 */
export const marketShortcuts = [
  { label: '10 万元以内', query: { maxPrice: '100000' }, photo: '/images/showcase/budget.jpg' },
  { label: '3 万公里以内', query: { maxMileage: '30000' }, photo: '/images/showcase/mileage.jpg' },
  { label: '代销车源', query: { saleMode: 'CONSIGN' }, photo: '/images/showcase/consignment.jpg' },
]
