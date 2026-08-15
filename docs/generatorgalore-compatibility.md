
## Generator family (Transform)

Twelve `auto_storage:generatorgalore_*_generator` PROCESS descriptors (one
work/tick each) plus time-based Transform uses, one per base generator
block. Each converts its fuel to FE = generationRate × work over
work = burnTime × consumptionRate ticks (or the SolidFuelMap/FOOD/
ENCHANTMENT overrides), reading the live GeneratorObject JSON registry and
SolidFuelMap datamap exactly like `GeneratorBlockEntity.tick`. Generators:
copper(8/1.2), gold(32/0.8), iron(16/1), diamond(64/0.6), emerald(256/0.4),
netherite(256/0.4), obsidian(128/0.6), netherstar(4092/2400), halitosis
(dragon breath, 128/1), culinary (food), enchantment (enchanted books,
retains the book), ender (ender pearls/eyes). Magmatic/Honey (fluid fuel)
and Potion (level-dependent resolver) generators are excluded. Verified
against Generator Galore 1.21.1-1.6.3 bytecode
(`GeneratorObject.getGenerationRateForItem`, `GeneratorUtil.calculate*`).
Copper coal: 1,920 work → 15,360 FE.
