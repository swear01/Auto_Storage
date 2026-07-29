# Fusion connected-casing fallback

Auto Storage always ships vanilla block models and ordinary 16x16 textures, so Fusion is not required to start or play the game.

When a client has Fusion 1.2.12 installed, `ClientSetup` registers the always-active built-in client resource pack at `resourcepacks/fusion_connected_casing`. That pack overrides only the world block models with Fusion connecting models and their `pieced` texture sheets. Installing or removing Fusion requires a client restart; the server and gameplay state are unchanged.
