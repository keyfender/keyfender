module type S = S.Keyring

module Make (KV: Irmin.KV_MAKER) : S
