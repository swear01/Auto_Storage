
## Coal Generator (Transform)

`auto_storage:rftoolspower_coal_generator` is a PROCESS descriptor (one
work/tick) plus a time-based Transform use: coal/charcoal converts over
`CoalGeneratorConfig.TICKSPERCOAL` (default 600) work ticks at
`CoalGeneratorConfig.RFPERTICK` (default 60) FE/tick; coal blocks run 9x
(5,400 work -> 324,000 FE). Only coal, charcoal, and coal blocks are
accepted, matching `isValidFuel`. In-world infusion upgrades scale both
ticks and RF/tick; the transform models the base rate. Verified against
RFTools Power 1.21-7.0.6 bytecode (`handlePowerGeneration`,
`CoalGeneratorConfig`, `isValidFuel`). Coal: 600 work -> 36,000 FE.
