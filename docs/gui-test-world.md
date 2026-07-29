# GUI Test World Contract

`MagicStorageGuiTest` 是由 `scripts/prepare_prism_gui_world.py` 每次依 scenario
重新產生的 true-void 測試世界，不是人工維護的 save。產生器與
`scripts/test_prepare_prism_gui_world.py` 一起構成 executable test；世界內容不符合
本文件時，應先修改 profile 與測試，不能進遊戲臨時補物品。

## 所有 scenario 的共同規則

- 每個 scenario 只建立本次 checklist 需要的方塊、Core baseline、導航與 player kit。
- manifest 必須明列 `start target`，玩家進場時已 `/tp facing` 第一個檢查目標。
- server-owned Core 只能有 one repository record；方塊實體只保存相同 storage UUID
  的 reference，不能複製 client storage state。
- Stored item NBT 必須依 production schema 切成每段最多 63 種的
  `inventorySegments`；90+ 種視覺資料不得塞進單一 segment。
- Component-bearing stored stacks也必須寫入同一production segment格式；
  `crafting-fuel-page`的19種Singularity以相同item ID加不同
  `extendedcrafting:singularity_id` component保存，不能壓平成普通item count。
- `crafting-fuel-page` scenario-owned datapack必須替10個default common-ingot tags提供
  test-only value，讓server與EMI都固定得到19-input Ultimate Singularity極端值；
  這些tag不可放入main resources或改變一般玩家的整合包。
- `player inventory` 預設為空。只有目視動作本身需要的物品可以放入 hotbar／inventory，
  且不得把重複安裝、等待 processing 或補資源留給玩家。
- 所有 repository-backed aggregate、typed resource、station work 與 stored item
  都必須同時由 manifest assertion 與 repository NBT assertion 守住；由網路拓撲
  決定的 type capacity 則由 manifest 與 setup block assertion 守住。
- GUI 改動必須更新對應 visual assertions；GameTest/Python 通過不能取代 F11 使用者判定。

## Scenario matrix

| Scenario | start target | 預載內容 | visual assertions |
|---|---|---|---|
| `boot-smoke` | overview | 無網路、空 player kit | client 能載入資源並進入世界；不做版面批准 |
| `terminal-left-rail` | storage_terminal | Core、Storage Terminal、Crafting Terminal；hotbar 1/2 只負責導航 | 兩種 terminal 的共用 rail、搜尋與置中 |
| `bus-configuration` | import_bus | fresh Core、Import/Export Bus、兩個容器及最小互動 kit | 方向、filter、wrench 與 reset 流程 |
| `crafting-fuel-page` | crafting_terminal | Creative Storage、完整代表性 items/typed resources、Transform reserves、Processing/Instant Stations、descriptor-keyed work；hotbar 3 的 Coal 是唯一視覺輸入 | 四頁共用 header/content bottom、自適應grid列數、立即row scroll、空白 top search、Transform logical source、Stations All/Installed與固定稀疏列、未安裝station明顯降低可見度、logical family tooltip、固定 type capacity、無外框recipe preview/header頁碼 |
| `terminal-scale` | crafting_terminal | on-demand 10,000或30,000個exact component variants、目前runtime registry每種非空item各`--items-per-type`個（預設64）、全部Processing/Instant descriptors、typed/work reserves、Storage/Crafting兩種Terminal、Creative Storage、單一production repository record、空玩家背包 | 兩種Terminal prepared open、Craftable與recipe preview、grab-offset thumb drag、track page/repeat、立即row wheel、framed widget states、長列表搜尋/排序/遠頁、4px recipe inset、craftable-first排序與`n / craftable (total)`；真正cold效能由matrix gate負責 |
| `patchouli-guide` | overview | Guide Book | 章節、文字、連結與版面 |

## `crafting-fuel-page` 高風險 fixture

- `magic_storage:mekanism_crusher` 預載
  `mekanism:ultimate_crushing_factory × Integer.MAX_VALUE`。這是 aggregate count，
  item prototype 的 encoded count 仍必須是 1。
- Processing icon overlay、總 rate 與 station work 必須在此數量下不溢位且可讀。
- Instant Stations 是二元解鎖，只顯示物品，不顯示 installed count 或假 work。
- Stations `All`與搜尋結果中的未安裝代表物必須明顯比已安裝物暗；已安裝物保持原始亮度。
- 四頁各自保存查詢，但搜尋欄都使用 Storage/Craftable 的 top search 位置與同一
  focused input/query pipeline；只有候選 scope 不同。只輸入裸 `@`、`#`、`$`時必須顯示全部且focus可留在欄位繼續輸入；點擊欄外則解除focus但保留query；`@mod`是namespace substring match，因此`@creat`必須命中Create。
- Storage/Craftable的resource selector只顯示已註冊群組，另永遠提供All聚合item與typed resources，tooltip必須精確為`Show: All`。Mekanism Gases的Oxygen/Hydrogen/Chlorine必須顯示各自名稱與EMI的colored chemical glyph，不得顯示Chemical Tank或Brewing Stand；recipe amount也不得誤用64,000 tank capacity。
- non-item grid必須讓Fluid使用青綠框、Energy使用紫色框、Gas/Other使用藍色框、Processing使用琥珀框，背景都必須足夠不透明且有實色邊框；Energy包含FE、Fuel/Brew、Mana與Source，Other包含`Axe Uses`及沒有更精確共同分類的addon值。Mana顯示Mana Powder/`Mana`而不是Mana Tablet，Source顯示Source Gem/`Source`而不是Source Jar。容器只用於實際存取，不得成為resource identity。
- fresh client config的`Use Player Inventory`預設Off。Transform/Stations顯示並重用
  Storage的Sort Method/Sort Order；非物品primary選中時Craft Output固定Storage且不可切到背包。
- Transform/Stations 搜尋欄空值時不顯示 placeholder；輸入文字後才顯示查詢本身。
- Processing 每頁使用固定欄寬與左上順序；最後一列只有一或兩項時也不得重新分配寬度。
- Transform card 顯示 logical station family；Powah tier variants 的來源統一為 `Furnator`。
- Transform card 緊接上方 input/action row、固定 32px 高並靠上排列；card 分頁鈕位於 card 區下方，單頁隱藏時不得留下空白頂列。
- 資源selector顯示`Processing`而不是內部key名稱`Station Work`；Processing數值hover必須分別顯示同步的logical family名稱，例如`Crusher`、`Energizing Rod`、`Furnator`，不得全部退回同一generic label。
- player inventory 右側 status panel 永遠只顯示 type capacity，不因 hover 改成重複的 card/station 資訊。
- Core 預載 Honey、FE、Mana、Hydrogen、Chlorine 與各自 exact item inputs/stations，
  checklist 可直接檢查 Honey Bottle、Energized Steel、Manasteel Ingot、Hydrogen Chloride，
  不得要求玩家先裝機器或搬運資源。
- Core另預載Modern Industrialization Aluminum Blade與Macerator；展開並hover其EMI
  recipe diagram不得因recipe widget使用負座標或外擴drawable而crash。
- Instant Stations 預載 Ultimate Crafting Table；Core預載Extended Crafting預設19種
  exact component Singularity各1個。Scenario不再注入假Diamond recipe；玩家直接選
  真實Ultimate Singularity，preview必須重用EMI完整9×9 public widget而不是native
  3×3摘要；ledger可用滾輪看完19個`1/1`材料列且×1 ready。短數值未溢出前維持單行，
  只有實際超過cell寬度才拆成available/required兩行。
- 世界產生後玩家不需要安裝 station、補充測試資源或等待 work；Coal 是唯一非導航
  的目視輸入，玩家只依 checklist 將它 Shift-click 到 Transform。

## `terminal-scale` 大型列表 fixture

- 只接受 `--scale-types 10000` 或 `30000`，不加入一般visual scenario，也不註冊
  10k/30k個production items。
- repository generator先寫入deterministic searchable exact component variants；進世界後，
  marker-gated `_gui_test_seed`會從實際`BuiltInRegistries.ITEM`枚舉全部runtime items，
  把每種非空default stack補到`--items-per-type`（正整數、預設64），而不是維護易漏的
  hardcoded清單。兩組資料都只屬同一production Core record；每個
  `inventorySegments`最多63種，BE只保存相同UUID reference。
- 同一seed transaction會安裝每個非Transform descriptor：Processing各130台（受descriptor
  上限約束）、Instant各1台，並填入全部built-in energy與descriptor station-work reserves。
  玩家不需要安裝機器、等tick或搬運資源。
- hidden seed/warm commands只在world root有
  `.magic_storage_runtime_fixture_pending`時可用；`_gui_test_warm_craftable`完成一次
  server-side Craftable prepare後才消耗marker並輸出
  `MS_GUI_RUNTIME_FIXTURE_READY`。runner在此log之前不得handoff。
- hotbar 1/2只提供Storage Terminal與Crafting Terminal導航，玩家背包其餘位置保持全空；
  進場已面向Crafting Terminal。玩家只做F11批准、開頁、搜尋、排序、
  wheel/track/thumb、選取綠色Craftable輸出並檢查recipe preview，不負責填資料。
- scrollbar必須區分thumb drag與track paging：thumb按下不跳，drag保留grab offset；
  track立即翻頁並在持續按住後重複；wheel每次立即一列。不得有easing、queue或回彈。
- Recipe panel上／側inset都是4px；相同exact output的可合成variant在前，header使用
  `n / craftable (total)`，切換資源後仍保留存在的exact selection。

## 變更流程

1. 先在 `scripts/test_prepare_prism_gui_world.py`／`scripts/test_run_prism_gui_session.py`
   寫會失敗的 contract test。
2. 修改 `SCENARIO_PROFILES`、Core baseline 或 checklist。
3. 跑：

   ```bash
   PYTHONPATH=scripts PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
     scripts.test_prepare_prism_gui_world \
     scripts.test_run_prism_gui_session
   ```

4. 視覺行為變更再跑：

   ```bash
   python3 scripts/run_prism_gui_session.py --scenario crafting-fuel-page
   python3 scripts/run_prism_gui_session.py --scenario terminal-scale --scale-types 30000
   ```

5. 只把 F11 fullscreen 的最終目視 verdict 記為 GUI verified。
