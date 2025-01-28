# Benchmarking

To run the benchmark, check out
[js-framework-benchmark](https://github.com/cjohansen/js-framework-benchmark)
and follow these steps:

```sh
git checkout https://github.com/cjohansen/js-framework-benchmark.git
cd js-framework-benchmark
npm ci
npm run install-server
npm start
```

Leave the server running.

## Compiling the test runner

In another terminal:

```sh
cd webdriver-ts
npm ci
npm run compile
```

## Build Replicant

Finally, build Replicant to run its benchmark:

```sh
cd frameworks/keyed/replicant
npm run build-prod
```

You should now be able to manually interact with Replicant on
http://localhost:8080/frameworks/keyed/replicant/

## Run the benchmark

With Replicant built, you can go to the root directory to run the benchmark:

```sh
npm run bench keyed/replicant
```

This will take about 5 minutes, and Chrome will open and close several times.
When it's done, generate the report:

```sh
npm run results
```

And open http://localhost:8080/webdriver-ts-results/dist/index.html

It is also possible to run an all-in-one benchmark, validation, and report. This
will run the benchmark headlessly:

```sh
npm run rebuild-ci keyed/replicant
```

## Comparing frameworks

It can be useful to compare to some other frameworks. To do so, build them and
run the benchmark for each one. Here are some suggestions:

```sh
cd frameworks/keyed/react
npm run build-prod
cd ../..
npm run bench keyed/react

cd frameworks/keyed/reagent
npm run build-prod
cd ../..
npm run bench keyed/reagent

cd frameworks/keyed/vanillajs
npm run build-prod
cd ../..
npm run bench keyed/vanillajs
```

## Benchmarking and testing changes

If you want to try to optimize or tweak Replicant, you might want to run several
benchmarks. For all I know, the directory name is used to key the frameworks in
the report, so my current workflow consists of copying the
`frameworks/keyed/replicant` directory, making changes to it, and running the
benchmark, e.g.:

```sh
cp -r frameworks/keyed/replicant frameworks/keyed/replicant-head
cd frameworks/keyed/replicant-head
cp -r ~/projects/replicant/src/replicant src/.
cd ../../../
npm run bench keyed/replicant-head
npm run results
```

If you make changes to the code, you can start by running a faster test, to
avoid running a long benchmark on broken code:

```sh
npm run isKeyed -- --headless true keyed/replicant-xyz
```
