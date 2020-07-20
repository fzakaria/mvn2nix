# mvn2nix

In the same spirit of [bundix](https://github.com/nix-community/bundix), **mvn2nix** creates a Nix set with the
*transitive closure* of all dependencies required by the application.

```bash
nix-shell -p mvn2nix --run 'mvn2nix 1> dependenciex.nix'


head dependencies.nix
{
	"org.slf4j:slf4j-api:jar:1.7.30" = {
		url = "https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/1.7.30/slf4j-api-1.7.30.jar";
		sha256 = "cdba07964d1bb40a0761485c6b1e8c2f8fd9eb1d19c53928ac0d7f9510105c57";
	};
	"org.slf4j:slf4j-simple:jar:1.7.30" = {
		url = "https://repo.maven.apache.org/maven2/org/slf4j/slf4j-simple/1.7.30/slf4j-simple-1.7.30.jar";
		sha256 = "8b9279cbff6b9f88594efae3cf02039b6995030eec023ed43928748c41670fee";
	};
	"com.google.guava:guava:jar:29.0-jre" = {
```

You can then use this to download all the necessary dependencies to run your application.

## Development

If you are running *mvn2nix* from this repository, you can do so with **nix-build**

```bash
nix-build

./result/bin/mvn2nix 1> dependencies.nix     
```