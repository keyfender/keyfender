module Dispatch (H:Cohttp_lwt.S.Server)(KR:Keyring.S)(DATE:Wm_util.Date_sig) : sig
  val dispatcher : KR.storage -> Cohttp.Request.t -> Cohttp_lwt.Body.t
    -> (Cohttp.Response.t * Cohttp_lwt.Body.t) Lwt.t
end
