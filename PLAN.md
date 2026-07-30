# Auto Storage Mod — 設計計劃書

> 靈感來源：Terraria Magic Storage  
> 目標平台：Minecraft（NeoForge）  
> 狀態：核心已實作（即時進度見 `docs/plan.md`；本文件為設計參考）

---

## 一、核心設計理念

### 設計哲學：讓玩家開心，不做人工障礙
- **All-in-One 工作站**：一個終端完成所有合成
- **物品數量無上限**：同一種類的數量不受限；種類容量由有限 Unit 提供，或由 Creative Storage Unit 明確切換為無限
- **合成能量無上限**：先累積後享用，不卡玩家
- **配方書式操作**：點選物品 → 下方面板顯示配方 → 點按鈕瞬間合成

---

## 二、合成能量系統（Crafting Energy）

### 核心概念
Storage Core 透過 Fuel 頁中實際安裝的處理機器累積「工作站能量」，把原本要等待的處理時間預先存起來，合成時一次消費。沒有安裝機器就不會憑空產生能量。玩家另可投入燃料物品補充對應的燃料能量池。

### 能量池：統一模型，差異只在來源

所有能量池結構相同（`Map<EnergyType, Long>`，無上限），差異在**來源**：

| 能量池 | 來源 | 用途 |
|--------|------|------|
| `smelting_energy` | Fuel 頁每台 Furnace +1/tick | 熔爐配方 |
| `blasting_energy` | Fuel 頁每台 Blast Furnace +1/tick | 高爐配方 |
| `smoking_energy` | Fuel 頁每台 Smoker +1/tick | 煙燻爐配方 |
| `campfire_energy` | Fuel 頁每台 Campfire +1/tick | 營火配方 |
| `brew_energy` | Fuel 頁每台 Brewing Stand +1/tick | 未來釀造 consumer 預留（目前不可執行） |
| `furnace_fuel` | 任何 NeoForge/vanilla runtime burn time > 0 的燃料 | 所有爐系配方額外消耗；對玩家顯示 **Fuel** |
| `blaze_fuel` | 塞烈焰粉/烈焰桿 | 未來釀造配方額外消耗；現階段可先儲存 |

工作台 shaped/shapeless、切石機 stonecutting、exact `SmithingTransformRecipe` 與 exact `SmithingTrimRecipe` 是不消耗 process/Fuel energy 的即時合成，必須在 Core 安裝 matching Crafting Table/Stonecutter/Smithing Table。Deterministic vanilla default-state axe strip/scrape/wax-off 也立即完成，但改為要求 Core 有足夠 finite/∞ Axe Energy；axe 不再作為可取回 station 安裝。兩種 Smithing recipe 都以實際來源的完整 `ItemStack` components 解出 source-bound variant，再由 joint allocation 呼叫 `assemble`；Transform 保留 base components，Trim 同時保留 base components並套用registry解析出的pattern/material。Trim 的多輸出variant以exact output identity區分，canonical排序且超過bounded combination cap即整個fail closed；dynamic/special/incomplete、缺少trim registry mapping、其他context-dependent recipe與任意mod block tool hook仍不支援。

### Recipe → 消耗的能量池

爐系成本必須由 server 目前 `RecipeManager` 中的具體 `AbstractCookingRecipe#getCookingTime()` 取得，不再按 `RecipeType` 手寫 200/100/600。Smelting / Blasting / Smoking / Campfire 各自消耗同 tick 數的對應 machine process pool 與 `furnace_fuel`。非正值、stale 或 unsupported recipe 一律 fail closed；preview 與 execute 共用同一 resolver。

工作台 shaped/shapeless、stonecutting、Smithing Transform與Smithing Trim不耗 process/Fuel energy。Axe transformation 每 craft 精確消耗 1 finite Axe Energy；infinite flag 不遞減。Brewing 尚無 production consumer，不以預留的固定數字冒充已完成支援。

### Fuel Value

`furnace_fuel` 的唯一 authoritative value 是 server commit 當下的 `ItemStack#getBurnTime(null)`；大於 0 就接受並按每件實際 burn ticks 加值。因此原木、datapack/NeoForge data-map 燃料、modded fuel 與 stack-sensitive override 不需要 Auto Storage 白名單。容器 remainder、overflow 與 conflict 仍沿用 simulate-then-commit/fail-closed 契約。Brew 是尚未接 production brewing 的 explicit reserved mapping，不可反過來覆蓋有效的 runtime Fuel value。退役的 legacy `bottle_fuel` 不在現行repository schema中，早期開發資料不遷移。

### 燃料塞入方式

Crafting Terminal（含綁定後的 Remote Terminal）有獨立 **Fuel 頁**：
- Fuel workspace沿用player inventory的vanilla light container grammar。獨立compact Fuel Target bar位於Consumables panel內；**Consumables**、**Timed Stations**、**Instant Stations** 三個全寬category panels由Crafting Terminal標題下方開始，平均分配到Inventory label band之前的完整垂直空間。每個panel左側是bounded category label，右側是會依實際寬高決定欄列數的multi-row paged flow；Consumables放input、Fuel/Brew reserves與Axe Energy，Timed Stations放Furnace/Blast Furnace/Smoker/Campfire/Brewing Stand，Instant Stations放Crafting Table/Stonecutter/Smithing Table。每格固定為上方18px slot/16px代表物、下方置中的自動縮放數字；空machine slot以dim代表物提示用途但不冒充installed item。Axe是CONSUMABLE input，不留persistent slot；finite value = remaining durability × (vanilla Unbreaking level + 1)，Unbreakable設∞。拒絕的input原樣保留；現行repository不讀legacy slot 8。
- 先選 `Auto` / `Fuel` / `Brew Energy`，再把燃料放進專用輸入槽；Fuel 頁 Shift+背包燃料也走同一 server-authoritative 轉換路徑。
- `Auto` 是每次開啟 menu 的預設。候選池依「可供應該池的 FuelTable distinct item 種類越少，優先度越高」排序；再以目前累計量較少、`EnergyType` enum 順序作穩定 tie-break。烈焰桿因此預設補 `blaze_fuel`。
- 三個category panels都由server-owned `auto_storage:machine_descriptor` registry的ordered snapshot產生geometry；內建九個descriptor固定在legacy順序，第三方依stable ResourceLocation排序。每頁容量為adaptive columns × rows，稀疏內容平均使用右側content bounds，overflow由各自wheel paging與頁碼處理，slot/tooltip只認實際slot/icon。Core/menu實體bank固定256格，opening payload同步immutable snapshot，通用consumable long/∞另由clientbound packet同步；mod數量不會改變container parity。SelfTest以每類64個descriptors鎖定多頁、全descriptor可達與不重疊。Type capacity不再佔用Instant Stations格位，而是在player inventory正右方的獨立info panel顯示完整localized `stored / max types`訊息；Instant Stations因此使用完整category width。Fuel Target的current-value selector與bounded foreground list popup位於獨立bar；left/right/wheel為next/previous，middle-click明確reset Auto，server仍只接Auto/exact IDs。第三方契約見`docs/machine-descriptor-api.md`。
- Storage 頁不轉換燃料；Shift+燃料和一般物品一樣存入網路。舊的燃料投入即時轉換 popup 不保留；目前 popup 只負責選擇 target。
- explicit target 不相容、non-fuel、overflow 或 stale request 全部 fail closed；container remainder 回專用槽/原玩家槽，溢出再回背包或顯式掉出。

Crafting menu 兩端固定 149 slots（81 display + 36 player + 1 Fuel input + 8 persistent process/instant slots + 1 transient Axe Energy input + 22 presentation metadata）；Storage base data 固定 12（最後一格是種類容量 explicit-unlimited bit），Crafting/Fuel 再加 89，總計 101 data slots。末五個 Crafting data values 是 Axe Energy 的四個 unsigned-16 words與 infinite flag。22 個 hidden slots 依序同步 exact output、9 個 item ledger representatives、station、9 個 positioned inputs、tool 與 typed metadata carrier；primitive availability/energy requirements仍以拆成 unsigned 16-bit words 的 data slots傳輸。Client 只由這些欄位重建 immutable `RecipePresentation`，不掃 `RecipeManager` 或 Core。目前 shared terminal/EMI/station/Axe revision 見 `docs/superpowers/specs/2026-07-14-terminal-platform-emi-recipe-axe-design.md` 與同名 implementation plan。

### 能量不足顯示

Crafting Terminal 配方詳情區使用deep blue-charcoal workspace，由上到下採 exact recipe diagram → resource ledger → actions footer：
- diagram 依 recipe kind 顯示 positioned 3×3 crafting、單輸入 cooking/stonecutting/axe 或三 role smithing，並繪製 exact per-craft output count、station 與 shapeless marker。
- diagram + ledger合併成footer上方置中的compact card，body最高108px、ledger最高36px，單一resource row垂直置中；尚未選物品或選中物品沒有支援配方時同一card顯示wrapped中性空狀態。不可讓fullscreen剩餘高度變成大型白底或有框空區。
- 每個 item/process/Fuel/Axe Energy resource 顯示目前 available 與單次 required；infinite Axe Energy 顯示 ∞。
- item ledger row 使用中性底色，energy/tool row 使用深紅底色；成功與否另外由 amount 文字綠/紅表示。
- 整體狀態顯示 `Ready ×N` 或 Missing，固定數量按鈕不足時變暗。
- `Max` 每次由 server fresh preview 算目前合法最大值，不信任 client 顯示數；若資源上限無法完整交付，會再找出當下可完整交付的最大值。Binary search 使用 long-safe arithmetic；Core reservation/extraction/output/rollback 直接走 per-key long-count API，一個 key 一次 mutation，因此 `Long.MAX_VALUE` 也不會溢位或形成數十億次 chunk loop。

### 批量合成
- **線性消耗**：合成 ×N 就消耗 ×N CE
- 能量不足時顯示「可合成 X 個（XX 能量不足）」

---

## 三、物品儲存系統

### 架構原則：所有計算在 Core，Unit 只是空間

- **Storage Unit 沒有自己的庫存**，它只是一個「容量貢獻方塊」
- Core 掃描到有限 Unit 後加總種類槽；掃描到一個以上 Creative Storage Unit 時以 explicit unlimited state 表示，不使用 `Integer.MAX_VALUE` sentinel
- 所有永久物品資料、庫存索引、能量池都存在overworld `CoreStorageRepository`的`CoreStorageRecord`；Core BlockEntity只保存UUID/schema reference與runtime cache/topology
- NBT 不同 = 不同種（帶附魔的劍 ≠ 普通劍）
- 每種物品數量無限，並聯 Unit 數量不限

### Storage Unit 各 Tier

| 方塊 ID | 貢獻種類槽 | 視覺階段 |
|---------|-----------|----------|
| `storage_unit_t1` | +10 種 | copper-bound simple cell |
| `storage_unit_t2` | +25 種 | iron braces / second node |
| `storage_unit_t3` | +50 種 | gold-lapis lattice |
| `storage_unit_t4` | +100 種 | diamond-quartz cross |
| `storage_unit_t5` | +200 種 | prismarine-ender halo |
| `storage_unit_t6` | +400 種 | netherite-amethyst crown |
| `creative_storage_unit` | 無限種類 | cyan-amethyst infinity cell |

- 各 Tier 是獨立方塊，合成需要上一 Tier 作為材料（強制線性升級）
- **不可逆**：每個tier破壞/扳手拆除都掉回自己，Core重新掃描後自動調整總額度；upgrade recipe仍只向上。
- Creative Storage Unit 只可由創造物品欄或指令取得，沒有生存配方、自己的庫存或物品生成能力；物品 tooltip 以 en_us/zh_tw 簡述「無限種類、不生成物品」。
- 有限種類槽仍加總保存；只要目前已載入、合法單 Core 網路中至少一個 Creative Storage Unit 連通，種類容量即為無限，多個效果相同。
- Storage/Crafting/Fuel 顯示共用 explicit unlimited bit；Fuel 無限時使用 Creative Storage Unit 圖示，有限時維持 T1 圖示。

### 配方進度契約

- Starter Core + Storage Terminal + 2x T1總共只用1 Diamond，其他是overworld black concrete powder/obsidian/copper/redstone/glass/wood，T1不再需要amethyst；目標是一到兩次採礦可建。
- Functional midgame的Crafting Terminal、Wrench、Import/Export Bus、Remote與T1–T3不要求Netherite、Ancient City、Trial Chamber或End completion。
- T4–T6是選擇性capacity/prestige；依序使用quartz/diamond/ender pearl、prismarine/blaze/ender pearl、amethyst block/netherite scrap/eye of ender，禁止Diamond Blocks與Netherite Ingots牆。
- Exact shaped matrices與材料數量以`docs/superpowers/specs/2026-07-14-connected-progression-fuel-guide-design.md`為準，並由recipe static/GameTest守護。

```
範例：
  storage_core 連接了：
    storage_unit    × 3  →  +30 種
    storage_unit_t2 × 1  →  +50 種
  ─────────────────────────────
  總可用種類槽：80 種（全部由 Core 統一管理）
```

### 超額處理
若破壞有限 Unit，或 Creative Storage Unit 被移除、斷線、卸載、扳手拆除或爆炸，Core 只重新計算容量，絕不刪除既有物品。若有限總額度低於現有種類數，網路回到既有超額狀態：現有種類仍可存取/追加，但拒絕新種類，直到額度恢復。多 Core conflict 與 currently-loaded bounded topology 規則不變。

---

## 四、方塊設計

> **連接規則：實體相鄰（六面），不需管道**  
> **每個網路只有一個 `storage_core`，作為系統偵測的唯一切入點**

### 核心方塊

| 方塊 | 功能 |
|------|------|
| `storage_core` | 網路唯一中樞；持有庫存、能量池、種類額度；BFS 掃描起點 |
| `storage_unit` ~ `storage_unit_t5` | 容量擴充方塊（貢獻 +10/+25/+50/+100/+200/+400 種類槽給 Core）；無自身庫存；並聯不限；升級不可逆 |
| `creative_storage_unit` | 創造模式專用種類容量方塊；有效單 Core 網路中提供 explicit unlimited，無庫存、無生成、無配方 |
| `storage_terminal` | Tier 1：網路物品格（可取可存），無配方面板 |
| `crafting_terminal` | Tier 2：網路物品格 + 配方面板，可合成 |
| `remote_terminal` | Tier 3：**物品**（非方塊）；功能同 T2；Shift+右鍵點 Core 綁定；右鍵開 GUI |

### 擴充方塊（第二階段）

| 方塊 | 功能 |
|------|------|
| `import_bus` | 自動吸入相鄰容器物品到網路 |
| `export_bus` | 自動從網路輸出物品到相鄰容器 |

---

## 五、Terminal 三個等級

三個等級是**不同方塊/物品**（參考 RS：Grid / Crafting Grid 是不同方塊），沒有頁籤切換，T2/T3 的配方面板永久顯示。

| 等級 | 方塊 | 功能 |
|------|------|------|
| Tier 1 | `storage_terminal` | 網路物品格（可取可存） |
| Tier 2 | `crafting_terminal` | + 配方面板（點物品顯示配方、一鍵合成） |
| Tier 3 | `remote_terminal` | 功能同 T2，但是手持物品，遠端開 GUI |

合成配方：`storage_terminal`（方塊）→ `crafting_terminal`（方塊）→ `remote_terminal`（物品）

---

### 5a. Storage Terminal GUI（Tier 1）

```
┌────────────────────────────────────────────────────────┐
│  [🔍 搜尋__________]              [排序 ▼] [檢視 ▼]   │
├────────────────────────────────────────────────────────┤
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┐                        │
│  │  │  │  │  │  │  │  │  │  │                        │
│  ├──┼──┼──┼──┼──┼──┼──┼──┼──┤  ← 網路物品 icon grid  │
│  │  │  │  │  │  │  │  │  │  │  （可取可存，可滾動）    │
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┘                        │
├────────────────────────────────────────────────────────┤
│                     玩家背包                            │
└────────────────────────────────────────────────────────┘
```

| 操作 | 行為 |
|------|------|
| 左鍵點網路物品 | 取出一疊到背包 |
| 右鍵點網路物品 | 取出一半到背包 |
| Shift+左鍵點網路物品 | 取出全部到背包 |
| Shift+左鍵點背包物品 | 該格物品存入網路 |
| 滑鼠拖曳背包物品到網路格 | 存入手持物品 |

---

### 5b. Crafting Terminal GUI（Tier 2）

Crafting Terminal 由左側 rail 的第一群組切換 **Storage / Craftable / Transform / Stations** 四頁，並用明顯間隔和 item-page 排序、搜尋 controls 分開。Storage只列已儲存物；Craftable列目前可合成outputs並顯示該output現有Core庫存，首個正常server tick預取最多81個visible entries，實際切頁不得同步重建全表。所有cycle controls統一left-next/right-previous/wheel-down-next/wheel-up-previous/middle-reset；defaults為Name、Ascending、Search Sync Off、Auto Focus On、Auto Transform Target、Player Craft Output與既有player-source default。只有boolean on/off控制可畫彩色狀態燈；page tabs用neutral selection，Craft Output/sort/search/Transform Target等value selectors不亮燈。Recipe client只讀server-synced exact presentation；empty prompt與有效diagram/typed ledger都使用compact dark card，footer的`×1 / ×8 / ×64 / Max`是同palette的單一segmented strip且依live craftable count整體dim。Transform提供明確input到typed resource的快捷轉換；Stations只分Processing與Instant兩區，各自支援前後頁與滾輪。

Block/item材質固定為原版16×16與同一dark chassis/cyan-amethyst palette；T1→T6由copper cell、iron brace、gold/lapis lattice、diamond/quartz cross、prismarine/ender halo、netherite/amethyst crown逐級增加對稱ornament，Creative Storage Unit則使用同family的cyan-amethyst infinity cell。World casing預設使用ordinary 16×16 vanilla models；安裝可選 Fusion 1.2.12 時，client 會自動啟用內建 `fusion_connected_casing` resource-pack overlay，以 `pieced` 80×16 connected sheets覆寫world model。item models維持ordinary 16×16，bus front保留方向語意；overlay跨全部12種network blocks的model必須用釘選1.2.12支援的single-`block` predicate array，禁止誤用1.3的`blocks`或`true`/`false` schema。Published metadata不得要求Fusion；Gradle只在隔離的`fusionRuntime` source set供client/data runs載入，server/GameTest runs與Auto Storage jar不包含它。Auto Storage Wrench加入並接受`c:tools/wrench`：右鍵旋轉directional bus，Shift+右鍵安全拆除；含資料Core掉落物只取得指向既有server repository record的compact recovery token。生成候選/metadata/contact sheets只留在`art/texture-generation/`。

```
┌────────────────────────────────────────────────────────┐
│  [I][F]  [🔍 搜尋__________]                            │
├────────────────────────────────────────────────────────┤
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┐                        │
│  │  │  │  │  │  │  │  │  │  │  ← 網路物品 icon grid  │
│  ├──┼──┼──┼──┼──┼──┼──┼──┼──┤                        │
│  │  │  │  │  │  │  │  │  │  │                        │
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┘                        │
├────────────────────────────────────────────────────────┤
│  [輸入圖／role] → [精確輸出數] [station]  配方 1/2    │
│  資源帳本：item neutral；energy/tool dark red          │
│  現有/單次：生鐵礦 64/1、Smelt 400/200、Fuel 800/200  │
│  [◀][▶]                         [×1][×8][×64][Max]    │
├────────────────────────────────────────────────────────┤
│                     玩家背包                            │
└────────────────────────────────────────────────────────┘
```

| 操作 | 行為 |
|------|------|
| 點選網路物品 | 選取該物品，下方面板顯示其配方詳情 |
| `[◀][▶]` | 同一產物的不同配方切換 |
| `[×1] [×8] [×64] [Max]` | 精確按鈕不足量時變暗；Max 由 server 重新 preview 後合成當下最大合法量 |
| 左側 `Storage / Craftable / Transform / Stations` | 明確分頁；Craftable 的 zero-storage synthetic entries 可選 recipe 但不可抽取 |
| 左側玩家背包圖示（完整 tooltip） | 開啟後合成材料也計入玩家可見的 36 格主背包+快捷列（不含 armor/offhand，優先扣網路） |
| 左側 Transform / Stations 頁按鈕 | Transform 以 target selector/list popup 選 Auto 或明確typed resource並提交轉換；Stations 用兩個獨立paged sections管理 Processing與Instant stations |

### 同物品多配方與資源身分

- 每個 `ItemKey` component variant 永遠各自顯示，不會因畫面大小或使用者模式而合併。
- 同一產物的不同配方仍用 `[<][>]` 切換，selection 綁 exact recipe/output identity。
- 右側 exact presentation 由 server 同步 recipe ID/kind、positioned inputs、output components/count、station，以及每個 ingredient predicate 與 process/Fuel/tool 的現有量、單次需求；整體可合成數仍以 joint reservation 為最終真值。

### 5c. 遠端存取（Tier 3 專屬）

`remote_terminal` 是純物品，永遠不放置到世界中。

```
綁定流程：
  手持 remote_terminal，Shift+右鍵點擊 storage_core
  → 物品記錄 Core 的持久 UUID + 座標 + 維度，tooltip 顯示已綁定座標

使用流程：
  手持已綁定的 remote_terminal，右鍵
  → 開啟完整 GUI（與 crafting_terminal 相同）
```

- 只限同維度，且 Core 所在區塊需已載入；選單不會自行載入區塊
- 綁定的是該 Core 的持久 UUID；同座標換成另一顆 Core 後必須重新綁定
- 未綁定時右鍵無反應，tooltip 顯示「未綁定」
- 可以合成多個分別綁定同一網路（多人共用）

---

## 六、網路連接邏輯

```
BFS 從 storage_core 向六個方向掃描：

  [unit][unit][unit]
     ↑
  [core] — [unit]
     ↓
  [terminal]

放置 callback 合併到 next-tick topology pass；單一安全成長走 bounded incremental add，破壞、批次或不確定拓樸才走 bounded full rebuild。
沒有 core 的連通群組不構成網路（所有功能停用）。

Terminal/Import Bus/Export Bus cache access path，但每次操作前驗證路徑連續、每格 chunk 目前已載入且仍是 network block；失效時只尋找 alternate loaded path，不得為驗證連線載入 chunk。Import Bus 正面主動 pull 外，所有面另註冊 stable insert-only item-handler capability，外部 automation 可被動 push；handler 不保存 stack、不允許 extract，simulate/execute 都回 exact remainder。Active/passive lookup 共用10-tick missing-Core negative cache，不能讓外部 pipe 每次 probe 都重跑 bounded BFS。兩種Bus已完成schema-1 server-owned config、legacy/raw migration、placement owner與structured actor identity；deterministic filter policy及automation gate已接到既有directional active Import、passive Import與active Export。Mode/side/unsided/owner控制仍待server configuration slice；Export Bus目前仍只做朝正面的主動輸出，無方向模式延後。

效能：Unit 沒有 BlockEntity，BFS 只做 `getBlockState()` 檢查；所有 BFS/full rebuild 都有深度與方塊數上限。
```

---

## 七、技術規劃

### 7.1 開發環境

| 項目 | 內容 |
|------|------|
| 平台 | NeoForge（Minecraft 1.21.1） |
| 語言 | Java 21 |
| 配方瀏覽 | **EMI**（release為client-only required dependency，接受`[1.1.24,2)`；compile/dev runtime用最低基準Modrinth version ID `5sIPA1To`（對應`1.1.24+1.21.1`），CI另解析並編譯最新相容1.21.1 release；dedicated server不要求EMI。Exact standard recipe上方diagram只走公開`EmiRecipe`/`Widget`/`WidgetHolder`，internal synthetic recipe走native renderer；handler只接受terminal支援的exact backing recipe，送`{recipeId,amount,destination}`；NONE選取preview，CURSOR/INVENTORY執行單層server-authoritative magic craft，不做遞迴pattern tree） |
| 說明書 | **Patchouli**（遊戲內指南書 library，`vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE`） |
| 能量系統 | 自訂，不用 Forge Energy（合成能量非通用科技電力） |
| UI | 全手刻（`AbstractContainerScreen` + `GuiGraphics`），無現成 UI library |
| macOS GUI visual validation | Prism visual session 的 `fullscreen:true` 由 `MacOsWindowMixin` 轉為 borderless Cocoa F11，禁止 GLFW attach monitor 或切 desktop mode；runner 在 READY 前後比對 desktop display mode。視覺 owner 是使用者；關閉固定 F11 → 有標題列的 windowed 視窗 → Command-Q。完整流程見 `docs/macos-fullscreen-guide.md`。 |
| 資料結構 | FastUtil（內建於 Minecraft，primitive collection） |
| 網路效能 | 自訂 BFS，純 `getBlockState()` 檢查，無 BlockEntity 開銷 |

### 7.2 資料結構（參考 AE2 儲存效能設計）

#### ItemKey：緊湊、不可變、NBT 感知的物品身分

```java
// 參考 AE2 的不可變 resource key 設計
record ItemKey(Item item, DataComponentMap components) {
    static ItemKey of(ItemStack stack) {
        // 必須 build 新 DataComponentMap；不可保留來源 stack 的 mutable patch view
        return new ItemKey(stack.getItem(), snapshot(stack.getComponents()));
    }

    ItemStack toStack(int count); // 套回完整 component snapshot
}
```

#### 兩層索引（Two-Tier Indexing）

參考 AE2 `KeyCounter` 設計，解決大數量物品的 O(1) 查詢：

```java
// Tier 1: 以 Item 為鍵（忽略 NBT），identity hash map
Map<Item, VariantStore> primaryIndex;

// Tier 2: 以完整 ItemKey 為鍵（含 NBT），記錄數量
class VariantStore {
    ItemKey2LongMap map;  // FastUtil Object2LongOpenHashMap<ItemKey>
}
```

- **非耐久物品**（方塊、錠、材料等佔大多數）：Tier 1 直接 O(1) 查詢，Tier 2 只有一個 entry
- **耐久物品**（劍、工具）：多 variant 共用同一 primary key，各自有不同 ItemKey
- **總種類數檢查**：`primaryIndex.size()` = 已使用的種類槽數

#### 儲存核心資料

```java
// 能量類型：process pools 由已安裝機器產生，fuel pools 由物品轉換
enum EnergyType {
    SMELTING_ENERGY(true), BLASTING_ENERGY(true), SMOKING_ENERGY(true),
    CAMPFIRE_ENERGY(true), BREW_ENERGY(true),
    FURNACE_FUEL(false), BLAZE_FUEL(false);

    final boolean machineGenerated;
}

// RecipeType → 能量消耗
record EnergyCost(EnergyType process, long processAmount,
                  EnergyType fuel, long fuelAmount) {}

class StorageCoreBlockEntity extends BlockEntity {
    UUID storageId;                        // chunk NBT reference
    int storageSchema;                     // fixed reference schema
    CoreStorageRecord attachedRecord;      // runtime alias, not a second persistent copy
    StorageTypeCapacity typeCapacity;      // 有限槽 + explicit unlimited（BFS/增量共用）

    // Fuel value 查表
    static Map<Item, List<FuelEntry>> FUEL_TABLE;  // Item → 哪些池 + 每個給多少

    // 快取層（參考 AE2 InventoryCache）
    Map<ItemKey, Long> cachedDisplay;       // GUI 顯示用快取，異動時重建

    @Override
    void serverTick() {
        if (conflicted) return;
        for (int slot = 0; slot < MachineEnergyTable.entries().size(); slot++) {
            var mapping = MachineEnergyTable.entries().get(slot);
            var stack = machines.getItem(slot);
            if (mapping.generatesEnergy() && mapping.accepts(stack))
                saturatingAdd(mapping.energyType(), stack.getCount());
        }
    }

    long insertItem(ItemStack stack, Action action, Actor actor) { ... }
    long extractItem(ItemKey key, long amount, Action action, Actor actor) { ... }
    boolean consumeEnergy(EnergyCost cost, long multiplier) { ... }
    boolean addAxeEnergy(ItemStack axe) { ... }     // atomic consume or reject unchanged
    boolean consumeAxeEnergy(long amount) { ... }   // finite decrements; infinite does not
    void rebuildNetwork(Level level) { ... }
    void addFuel(ItemStack stack, EnergyType targetPool) { ... }
}

class CoreStorageRecord {
    UUID storageId;
    UUID networkId;
    Map<Item, VariantStore> primaryIndex;
    Map<EnergyType, Long> energy;
    Container machines;                    // fixed 256-slot bank, stable descriptor IDs
    Map<ResourceLocation, Long> consumables;
    Set<ResourceLocation> infiniteConsumables;
    List<CompoundTag> unresolvedEntries;   // raw addon data survives load/save
}

class CoreStorageRepository extends SavedData {
    Map<UUID, CoreStorageRecord> records;   // overworld durable owner
    Map<UUID, UUID> recoveries;             // recovery UUID → same storage UUID
    Map<UUID, Attachment> attachments;      // runtime-only exact owner lease
    // inventory serialization partitions entries into arbitrary segments of at most 63 types
}

class VariantStore {
    Object2LongOpenHashMap<ItemKey> items = new Object2LongOpenHashMap<>();
    long get(ItemKey key) { return items.getLong(key); }
    void add(ItemKey key, long amount) { items.addTo(key, amount); }
}
```

Core建立時便由overworld `CoreStorageRepository`永久擁有完整payload；`StorageCoreBlockEntity`只保存storage UUID/schema。含資料Core在survival、creative、explosion或Wrench拆除時只新增recovery UUID→既有storage UUID的indirection，掉落Core攜帶該UUID與type/item summary，不建立第二份payload。放置成功後原子claim同一record，複製token不能複製內容；玩家遺失owned token時可執行`/auto_storage recover_core`重發同一未claim capability。每個inventory persistence segment最多63種但segment數不限；missing/corrupt/packed/duplicate attachment全部fail closed，raw addon entries原樣保留。舊inline Core NBT與舊full-snapshot recovery格式刻意不遷移。

#### 關鍵效能策略

| 策略 | 來源 | 說明 |
|------|------|------|
| 兩層索引 | AE2 KeyCounter | Item 層 identity hash + ItemKey 層 component hash |
| 預先計算 hash | AE2 AEItemKey | ItemKey 建構時 hashCode 就算好 |
| 不可變物件 | AE2 | ItemKey final，內部 stack 防禦性拷貝 |
| 顯示快取 | AE2 InventoryCache | GUI 用的物品列表緩存，只在異動時重建 |
| long 計數 | AE2 MEStorage | 數量用 long 而非 int |
| simulate/execute | RS2/AE2 | `Action.SIMULATE` 不 mutation/event；`Action.EXECUTE` 才 commit + `setChanged()` |

### 7.3 合成流程

```
玩家點「合成 ×N」
  → 依 recipe id 從目前 server RecipeManager（或 exact synthetic axe catalog）重取配方，確認 exact static contract + output identity
  → 重新驗證 matching Crafting Table / cooking machine / Stonecutter / Smithing Table 已安裝，或 Core 有足夠 finite/∞ Axe Energy
  → 對本次 preview/execute 建立一次 Core + 玩家材料 snapshot，保留每個 Ingredient predicate 身分
  → 建立 joint reservation，計算所需材料總量與可做上限
  → 若 [背包開關] 開啟：材料來源 = 網路庫存 + 玩家可見 36 格（優先扣網路；不含 armor/offhand）
  → 若 [背包開關] 關閉：材料來源 = 網路庫存
  → 檢查材料是否足夠
  → 若為爐系配方：檢查工作站能量池 + fuel_energy 是否足夠
  → 若為工作台/切石機/Smithing Transform/Smithing Trim：不消耗 process/Fuel energy；若為 axe transformation：另預留 Axe Energy × N
  → 先在扣除 player ingredients 的 36 格 snapshot 放產物/remainders，overflow 規劃回 Core；兩邊無法完整容納就整批 no-op
  → 進入 deferred-listener mutation batch，再重驗並從各 exact source 扣除材料 × N
  → 插入 Core overflow、從能量池扣除 process/Fuel × N、扣 finite Axe Energy（∞不扣）、套用玩家 inventory/cursor
  → 任一步不符即按 exact source rollback；listener callback 只在 batch 結束後送出
  → requested output 永不掉到世界或靜默消失
```

---

## 八、開發里程碑

| 階段 | 內容 |
|------|------|
| M1 | 方塊 & BlockEntity 註冊、模型、材質 |
| M2 | 網路 BFS 掃描、Core ↔ Unit 連通邏輯 |
| M3 | Storage Unit 六個 Tier 方塊（獨立方塊、不可逆、並聯不限） |
| M4 | `storage_terminal`（Tier 1）：物品格 GUI、取出/存入操作 |
| M5 | 能量池系統（EnergyType、公開machine descriptor registry、Core stable-ID持久化、固定256 slots、按安裝數量累積、燃料轉換、generic finite/∞ consumables、RecipeType 映射、NBT） |
| M6 | `crafting_terminal`（Tier 2）：配方面板、一鍵合成 |
| M7 | 即時合成（工作台、切石機、component-preserving Smithing Transform、exact component-preserving Smithing Trim、axe strip/scrape/wax-off）；matching station/Axe Energy gates |
| M8 | 熔爐類配方整合（smelting/blasting/smoking/campfire + fuelEnergy 消耗） |
| M9 | 同物品多配方（完整 `ItemKey` variants、exact recipe identity、`[◀][▶]` 配方切換） |
| M10 | `remote_terminal`（Tier 3 物品）：Shift+右鍵綁定、右鍵遠端 GUI |
| M11 | Import/Export Bus 自動化、NBT 物品顯示優化、背包開關 |
| M12 | Patchouli 遊戲內指南書（資源已加入且 content 路徑已修;Prism dev 0.1.3 log 已載入 17 jsons,實機開書可看到分類與說明內容;後續僅剩文案擴充/修整） |
