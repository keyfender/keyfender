module Dispatch (H:Cohttp_lwt.Server)(KR:Keyring.S) : sig
  val dispatcher : KR.storage -> Cohttp.Request.t -> Cohttp_lwt_body.t
    -> (Cohttp.Response.t * Cohttp_lwt_body.t) Lwt.t
end
