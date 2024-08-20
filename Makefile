clean:
	rm -f replicant.jar

replicant.jar: src/replicant/*
	clojure -A:jar

deploy: replicant.jar
	mvn deploy:deploy-file -Dfile=replicant.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

node_modules/ws:
	npm install ws

test:
	clojure -A:dev -M -m kaocha.runner clj

test-cljs: node_modules/ws
	clojure -A:dev -M -m kaocha.runner cljs

.PHONY: clean deploy test test-cljs
