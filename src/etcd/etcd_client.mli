open Protocol_j

module Make (Client: Cohttp_lwt.S.Client) : sig
    type result = (response, error) Result.result
    type key = string list
                                        
    val put: string -> key -> ?ttl:int -> string -> result Lwt.t 
    val get: string -> key -> result Lwt.t
                                        
    val delete: string -> key -> result Lwt.t
    val create_dir: string -> key -> ?ttl:int -> unit -> result Lwt.t

    val refresh: string -> key -> ttl:int -> unit -> result Lwt.t
                                                
    val list: string -> key -> ?r:bool -> unit -> result Lwt.t
    val rm_dir: string -> key -> result Lwt.t                                   
                                    
    val watch: string -> key -> (response -> 'a Lwt.t) -> ?times:int -> unit -> 'a Lwt.t
end
