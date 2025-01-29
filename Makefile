clean:
	rm -fr replicant.jar .shadow-cljs dev-resources/public/js .shadow-cljs target

replicant.jar: src/replicant/*
	clojure -A:jar

deploy: clean test test-compile replicant.jar
	mvn deploy:deploy-file -Dfile=replicant.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

node_modules:
	npm install

test:
	clojure -A:dev -M -m kaocha.runner clj

test-cljs: node_modules
	clojure -A:dev -M -m kaocha.runner cljs

test-compile: node_modules
	npx shadow-cljs release app

.PHONY: clean deploy test test-cljs
