# macOS Minecraft F11 全螢幕與關閉指南

本指南是 Prism dev 視覺 GUI session 在 macOS 的現行契約。它只適用開發測試 instance `AutoStorageGuiTest`；不改變玩家一般啟動方式。

## 保證與原因

macOS Retina 桌面可用點數尺寸不一定是 GLFW 可切換的顯示模式。若讓 Minecraft 的原生 F11 路徑把 GLFW 視窗 attach 到 monitor，GLFW 可能選擇另一個實體解析度，造成整個 macOS 桌面短暫變更解析度。

`MacOsWindowMixin` 因此只在 macOS 改寫 Minecraft F11：它把 Java 視窗設為無邊框 Cocoa 視窗、隱藏 Dock/menu bar，且 **絕不把 GLFW 視窗 attach 到 monitor，也不選擇或切換桌面 display mode**。非 macOS 仍走原版 GLFW 行為。

## 啟動與預檢

```bash
python3 scripts/run_prism_gui_session.py --scenario terminal-left-rail
# 或
python3 scripts/run_prism_gui_session.py --scenario bus-configuration
# 或
python3 scripts/run_prism_gui_session.py --scenario crafting-fuel-page
# 或，大型exact-type列表
python3 scripts/run_prism_gui_session.py --scenario terminal-scale --scale-types 30000
```

runner 會：

1. 先從明確的 `/Applications/Prism Launcher.app` 讀版本並要求11.0.3+。
2. `crafting-fuel-page`先驗證Prism dev中的20份support jars各只有一份，且SHA-256和`./gradlew stagePrismGuiSupportMods`產生的`build/prism-gui-mods`完全一致；其中包含MacFix 0.1.0、Flux Networks、Mekanism與其`mekanismgenerators`依賴，以及各一份GuideME、Curios、Extended Crafting與Cucumber。PneumaticCraft因零accepted production contract不加入；EvilCraft/Cyclops Core因TMRV 0.9.0 JEI stub會讓EvilCraft 1.2.91 Spirit Furnace packet在registrar建立前造成client FATAL，也不加入combined pack。任何JEI jar都會因與TMRV不相容而fail。`python3 scripts/deploy_prism_dev.py`會把全部support jars與Auto Storage、Fusion放在同一transaction部署/rollback，成功時移除舊JEI、EvilCraft與Cyclops Core。
3. runner固定將dev instance的`LowMemWarning`設為`false`。Prism 11.0.3在macOS memory pressure不是Normal時會開一個parentless `High memory pressure` modal；CLI無法回答時，launch會永久停在`EnsureAvailableMemory`而沒有Minecraft process。測試runner只略過這個launcher確認框，不關閉其他應用程式，也不降低Minecraft配置；若舊run已卡在該modal，先由使用者在Prism取消該launch，再重跑runner。
4. 要求一般Prism Launcher已開啟且account initialization完成；若沒有warm normal-root process，runner在改世界與啟client前fail。這避免Prism cold start即使帶`-o`仍刷新Microsoft/Xbox ownership。runner不建立`-d` data root，也不建立或改寫`accounts.json`。Offline-only root沒有owning account，Prism會進Demo/account-selection，不能拿來啟動完整遊戲。
5. 對該已執行process送出 `"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" -l dev -w AutoStorageGuiTest -o AutoStorageBot`。這是Prism官方CLI的既有instance離線launch路徑，不透過`open -n`。
6. launcher subprocess只帶HOME/PATH/TMPDIR/locale等必要環境；run artifact會移除Prism列出的process/native environment。
7. 寫入 `fullscreen:true`，移除任何舊的 `fullscreenResolution`；`overrideWidth=1280`、`overrideHeight=720` 僅供離開全螢幕後的 windowed fallback。
8. 在準備世界時記錄 macOS 桌面 display mode（點數、像素、refresh、depth）。
9. 等 `AS_GUI_TEST_READY` 後只掃normal-root `PrismLauncher-0.log`的本次cursor片段；任何`AuthFlow:`實際step或Microsoft/Xbox/XSTS/Minecraft-services endpoint都fail closed。generic Offline task與`RefreshSchedule` bookkeeping不代表網路登入。
10. 再讀一次 desktop mode；任一欄不同也fail closed。
11. 將 `manifest.json`、`session.json`、`checklist.md`、Minecraft log、已清理的`prism-launcher.log` 與 shutdown artifacts 寫進同一run directory。

visual scenario 的 owner 是使用者。READY、GameTest 或 client smoke 都不等於 GUI 視覺驗收。

## 使用者全螢幕 gate

READY 後先確認完整遊戲內容、左下版本文字、底部 hotbar 與 GUI 坐標都沒有被裁切；通過後才可執行該次 `checklist.md` 的 `u`、hotbar、點擊、滾輪或截圖步驟。

`crafting-fuel-page`是一個batched visual gate。世界在handoff前已把代表items、optional stations（含Powah Furnator與Ultimate Crafting Table）、Processing work、Fuel/Brew/Axe、fluid/FE/chemical/Source/Mana與recipe材料寫入唯一server-owned Core record；代表Crusher的Processing aggregate預載為`Integer.MAX_VALUE`，但prototype count仍為1。Honey Bottle、Energized Steel、Manasteel Ingot、Hydrogen Chloride與真實Ultimate Singularity的19種exact component材料都可直接預覽，不需setup。玩家只保留hotbar `1`/`2`導航物與hotbar `3`的一顆Coal作Transform Fuel/FE多用途視覺輸入。不得要求使用者逐台安裝、逐項補資源、等待work或先完成craft；這些行為由GameTest/fixture負責。使用者只需通過fullscreen gate、開Storage/Craftable/Transform/Stations、把預載Coal shift-click進Transform並目視。四頁寬版content bottom應一致對齊player inventory上緣；四頁搜尋欄共用top header、focused input router與query parser，只有scope不同。裸`@/#/$`必須等同空查詢並顯示全部，欄內可繼續輸入，點擊欄外則解除focus但保留query；`@creat`必須命中Create，空值不畫placeholder。Transform檢查persistent target list/search/page、Auto exact-input Show Uses、上方可見input與amount strip、明確card selection及card內聯source/station-work；card緊接input/action row、固定32px高並靠上排列，card分頁鈕在card區下方，單頁隱藏時不留下空白頂列；Powah tier variants的source統一顯示logical `Furnator`，不再有底部selected preview。Stations固定只有Processing/Instant兩區，但可見列數依可用高度自適應；F11代表尺寸下Processing較寬且三欄，安裝數疊在item、旁邊為累積work，最後一列不足三項仍保持相同欄寬並靠左靠上；Instant則為不顯示installed count或work的compact icon grid。兩區各自有前後頁與滾輪；`Show: All`必須顯示全部已註冊descriptor並以灰階標示未安裝項，`Show: Installed`只保留已安裝項，非空搜尋也遵守同一scope後才合併stations；inventory旁status panel永遠只顯示type capacity，不隨hover變成重複資訊。Storage/Craftable必須依可用高度填滿所有完整列（目前F11代表尺寸超過9列）；scrollbar有完整recessed frame與enabled/pressed/disabled狀態，thumb按下不跳並保留grab offset，track按下翻頁且持續按住會重複，wheel每次立即移動一列；不得有排隊、easing、彈性或回彈。resource selector另有聚合item/typed keys的All，tooltip精確為`Show: All`；Gases保留Oxygen/Hydrogen/Chlorine名稱並使用EMI colored chemical glyph與exact recipe amount，不能顯示Chemical Tank/Brewing Stand或64,000 tank capacity。Energy資源頁顯示FE與可轉換reserve，**Processing**獨立顯示descriptor-keyed加工量；數值hover必須顯示各自的logical family名稱與aggregate rate，不得全部顯示`Station Work`。fresh `Use Player Inventory`應為Off；Transform/Stations重用Storage Sort Method/Order；non-item output鎖Storage。Craftable warm return應立即使用目前自適應viewport cache且不得產生新keep-up warning；MI Aluminum Blade Macerator EMI diagram必須可render/hover且不能crash。Recipe workspace不得再畫包住整個右側的冗餘外框；diagram/ledger上方與左右皆使用4px inset，EMI public widget原尺寸放得下就不縮，否則完整等比縮放。相同exact output的可合成variant必須在前，rerank保留exact selection；右上顯示`n / craftable (total)`。Ultimate Singularity必須顯示EMI/TMRV完整9×9 widget而不是native 3×3摘要；ledger的`1/1`等短數值保持單行，只有實際溢出才拆兩行。Recipe ledger、station badge 1000ms輪播、registered resource groups與optional-mod recipes仍由使用者目視判定。完整world/profile/checklist契約見[`docs/gui-test-world.md`](gui-test-world.md)。EvilCraft GUI不在combined gate，只用隔離GameTest證據。

`terminal-scale`只在大量列表／scrollbar／recipe-ordering變更時on demand產生。`--scale-types`只接受10,000或30,000；世界使用一筆production repository record與每段≤63種的exact component variants，玩家背包為空且已面向Crafting Terminal。使用者只需F11批准後目視thumb drag不跳、track page/repeat、wheel立即一列、遠頁與搜尋/排序，以及4px recipe inset、craftable-first順序與`n / craftable (total)`；不需要準備或搬運任何物品。

## Active GUI utility: MacFix 0.1.0

使用者指定的是[`macfix`](https://modrinth.com/mod/macfix)，不是同名的macOS輸入修正模組。它是自有的NeoForge 1.21.1 client-only修復：在GLFW的Cocoa `NSWindowDelegate`缺少`windowWillReturnFieldEditor:toObject:`時安裝回傳`nil`的stub，避免macOS 26+在borderless/F11視窗切換`styleMask`或關閉時以unrecognized selector終止。

Modrinth project目前仍在審核、公開API回404，所以本輪依使用者明確同意，`stagePrismGuiSupportMods`從相鄰repo的`../macfix/build/libs/macfix-0.1.0.jar`stage，並固定驗證SHA-256 `79904d59892c4c5384811a384f3ce88aa5b3d6e8224dbde1b78dc2f80020080c`。缺檔或hash不符直接失敗；Modrinth公開後改用immutable version ID。MacFix只屬macOS Prism GUI support transaction，不進dedicated server、GameTest、Auto Storage release metadata或玩家required dependency。

目前驗收必須確認：

1. current-run log同時包含`macfix 0.1.0 loaded`與`macfix: installed windowWillReturnFieldEditor stub`。
2. Minecraft F11仍由Auto Storage的borderless mixin控制，不變成GLFW monitor fullscreen，desktop mode完全不變。
3. 仍依F11 → bordered window → Command-Q關閉，沒有`windowWillReturnFieldEditor:toObject:` unrecognized-selector crash，且`shutdown.json`為graceful。

在這個current GUI gate由使用者通過前，仍禁止直接從F11 fullscreen按Command-Q；MacFix不能成為放寬display safety或跳過watchdog證據的理由。

禁止使用 macOS 綠色按鈕、Control-Command-F，或將 macOS native fullscreen 與 Minecraft F11 疊加。

## 唯一允許的關閉順序

1. 按 **F11** 離開 Minecraft borderless fullscreen。
2. 確認正常、有標題列的 windowed 視窗已出現。
3. 再按 **Command-Q**。

不得直接在 F11 全螢幕按 Command-Q，也不要用廣泛的 `pkill java`。若本次 log 已出現 `Stopping!` 而該次精確 Java process 五秒仍未結束，runner 的 exact-PID watchdog 才會只終止那個測試 client。

關閉後檢查同一 run 的 `shutdown.json`：`graceful` 代表自行結束；`forced_after_glfw_shutdown_stall` 代表 watchdog 收尾，應附帶該 run artifacts 回報。watchdog 是測試 session 的最後防線，不是正常關閉成功的替代證據。

## 排查

- desktop mode 驗證失敗：先喚醒並解鎖顯示器後重跑；不要手動設定 `fullscreenResolution` 來繞過。
- 畫面遭裁切或全螢幕 gate 不通過：停止該 run，不要改用 macOS native fullscreen。
- 關閉後黑窗或 watchdog 介入：保留該 run directory 的 `shutdown.json`、`shutdown-watchdog.log` 與 `log-excerpt.log` 再排查。

相關實作：`src/main/java/com/swear/autostorage/mixin/MacOsWindowMixin.java`；流程細節：[`docs/notes.md`](notes.md#prism-dev--manual-handoff)。
