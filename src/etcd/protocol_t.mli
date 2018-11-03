(* Auto-generated from "protocol.atd" *)
              [@@@ocaml.warning "-27-32-35-39"]

type node = {
  key: string;
  createdIndex: int;
  modifiedIndex: int;
  expiration: string option;
  value: string option;
  dir: bool option;
  nodes: node list option
}

type response = { action: string; node: node; prevNode: node option }

type error = { errorCode: int; message: string; cause: string; index: int }
