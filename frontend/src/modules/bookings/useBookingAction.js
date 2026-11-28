/**
 * 预约和委托对话框共用提交锁、错误状态和成功刷新。
 * @author pangzhihao
 */
import { ref } from 'vue' // 将对话框状态与页面渲染绑定。

export function useBookingAction(page) {
  const target = ref(null) // 保留当前业务记录的快照，用于后端确认。
  const visible = ref(false) // 控制对话框是否显示。
  const note = ref('') // 保存工作人员本次填写的处理说明。
  const saving = ref(false) // 发送请求期间禁用重复提交。
  const error = ref('') // 在对话框内展示校验错误。
  const notice = ref('') // 在列表上方展示提交反馈。

  function open(row) {
    target.value = row // 选择当前操作的唯一记录编号。
    note.value = '' // 打开新记录时清除上次输入。
    error.value = '' // 清空之前记录的操作错误。
    notice.value = '' // 清空上次操作的成功反馈。
    visible.value = true // 展开对话框。
  }

  async function run(save, success) {
    if (!target.value || saving.value) return // 没有操作目标或正在保存时不再发送请求。
    saving.value = true // 在请求期间禁用提交按钮。
    error.value = '' // 清除上次失败提示。
    try {
      await save(target.value, note.value.trim()) // 交给页面提交记录编号和处理说明。
      visible.value = false // 操作成功后关闭对话框。
      notice.value = success // 反馈已保存的结果。
      await page.load(1) // 刷新第一页的筛选结果及记录总量。
    } catch (failure) {
      error.value = failure.message // 保留表单输入，提示服务端返回的具体原因。
    } finally {
      saving.value = false // 恢复按钮状态，允许处理下一条记录。
    }
  }

  return { target, visible, note, saving, error, notice, open, run } // 返回页面绑定对话框需要的完整状态。
}
