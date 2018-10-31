(* Keyring implementation taking a KV constructor *)

module Make (KV_Maker: S.KV_Maker) : S.Keyring
