.PHONY: dev lint

dev:
	clojure -A:cider:portal:dev-cljs:shadow-cljs watch bootstrap server client

lint:
	clj-kondo --lint src
