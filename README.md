Node.js example with ClojureScript with [shadow-cljs](https://github.com/thheller/shadow-cljs)
----

- [Shadow-cljs](http://shadow-cljs.org/)
- [Integrate with NPM modules](https://shadow-cljs.github.io/docs/UsersGuide.html#npm)
- [ClojureScript cheatsheet](https://cljs.info/cheatsheet/)

### Develop

Install all dependencies.

```bash
npm run install
```

Watch compile with with hot reloading, using the `app` target as example.

```
npm run watch:app
```

The above command will also start a server at port `8000` so that you can open `http://localhost:8000` and load the website.

### Production release

Build production builds for each target.

```bash
# Build all targets
npm run release
# or
npm run release:app
```

### REPL

Start a REPL connected to current running program, `app` for the `:build-id`:

```bash
npx shadow-cljs cljs-repl app
```

### License

MIT
