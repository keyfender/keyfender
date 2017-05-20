
module Dispatch (H:Cohttp_lwt.Server) : sig
  val dispatcher : Keyring.storage -> Cohttp.Request.t -> Cohttp_lwt_body.t
    -> (Cohttp.Response.t * Cohttp_lwt_body.t) Lwt.t
end
