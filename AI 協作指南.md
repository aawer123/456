AI 協作指南.md 範本
內容包含協作流程、命名規範與檢核清單。

1. 文件概述
此文件旨在定義與 AI 開發相關的協作慣例，確保需求一次到位、不改動既有版面（XML）、並快速回饋微調結果。

適用對象：產品經理、前端／後端工程師、測試人員

更新週期：有重大流程或命名規範調整時，請同步更新

2. 協作流程
收集需求

PM 在 issue 模板中填寫：

功能目的

主要流程步驟


嚴格遵守命名規範

不直接修改既有 XML，所有新增 UI 請放在新的 layout 檔

每個 commit 限一個大功能或一組微調

測試與回饋

Tag 格式遵循 SemVer（v1.2.3）

更新CHANGELOG.md


如需微調動效或排版，請標註「不動既有 XML，只新增 override」

3. 命名規範
類型	範例	說明
Branch	feature/123-login-button-hover	ticket-編號 + 簡短英文描述
Issue	AI-REQ-45	AI 需求第 45 號
XML Layout	layout_ai_chat_message.xml	layout_<領域>_<功能>_<variant>.xml
ViewModel	ChatMessageViewModel	PascalCase，與檔名對應
Resource ID	@+id/btn_send_message	prefix: btn_、tv_、iv_ 等等
4. 檢核清單
[ ] Issue 模板：需求完整、格式正確

[ ] Branch 名稱：符合規範

[ ] Commit message：主動描述改動，不超過 50 字

[ ] 代碼檢查：無警告、無未使用的 import



