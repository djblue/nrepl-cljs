# nrepl-cljs

Initial implementation of [nrepl](https://github.com/nrepl/nrepl) in
boostrapped clojurescript for node.

## development

To get started developing in this project, do:

    clojure -A:shadow-cljs watch bootstrap server client

To start the nrepl server on port 7888, do:

    node target/server

To connect the nrepl client to the server, do:

    node target/client

## status

Currenly, the only real op supported by the server is eval. Contributions
are welcome!

