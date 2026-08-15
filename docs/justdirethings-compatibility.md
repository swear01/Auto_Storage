
## Simple Coal Generator (Transform)

`auto_storage:justdirethings_generator_t1` is a PROCESS descriptor (one
work/tick) plus a time-based Transform use: any smelting-burnable fuel
converts to burnTime × `generator_t1_fe_per_fuel_tick` (default 15) FE
over floor(burnTime / `generator_t1_burn_speed_multiplier` (default 4))
work ticks, retaining crafting remainders (e.g. buckets). Verified against
Just Dire Things 1.5.7 bytecode (`GeneratorT1BE`: feRemaining =
burnTime × getFePerFuelTick, maxBurn = floor(burnTime / multiplier);
`Config` defaults). Coal: 400 work → 24,000 FE.
