clean:
	rm -f replicant.jar

replicant.jar: src/replicant/*
	clojure -A:jar

deploy: replicant.jar
	mvn deploy:deploy-file -Dfile=replicant.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

.PHONY: clean deploy
