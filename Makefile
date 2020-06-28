.PHONY: dev lint test clean

dev:
	clojure -A:cider:portal:dev-cljs:shadow-cljs watch bootstrap server client

lint:
	clj-kondo --lint src test

test:
	clojure -A:shadow-cljs compile test
	node target/node-tests.js

clean:
	rm -rf target/
