# DevContainer Notes

## Securing Environment Variables

`NOTION_API_TOKEN` needs to be passed securely via the DevPod CLI when starting the environment:

```bash
devpod up <repo> --workspace-env NOTION_API_TOKEN=<token> --ide vscode
```

Spring Boot automatically reads this uppercase environment variable directly, so it does not need to be hardcoded or mapped into the `devcontainer.json` configuration file.
