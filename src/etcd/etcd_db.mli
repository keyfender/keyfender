(* Etcd implementation of the KV constructor *)

module Make
    (Client: Cohttp_lwt.S.Client)
    (Enc: S.Encryptor)
    (Conf: S.EtcdConf)
    : S.KV_Maker
