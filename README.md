# nrepl-cljs

Initial implementation of [nrepl](https://github.com/nrepl/nrepl) for
[lumo](https://github.com/anmonteiro/lumo).

[![Build Status](https://travis-ci.org/djblue/nrepl-cljs.svg?branch=master)](https://travis-ci.org/djblue/nrepl-cljs)

## start

To start an nrepl server on port 7888, do:

    npm start

## test

To run tests, do:

    npm test

##  proxy

To run a tcp proxy to investigate the nrepl protocol, do:

    npm run proxy <destination-port>
