# Como configurar os secrets para assinatura no GitHub Actions

Para que o job `sign` do workflow funcione automaticamente em tags de versão,
configure os seguintes secrets no seu repositório GitHub:

> Settings → Secrets and variables → Actions → New repository secret

## Secrets necessários

| Secret | Descrição |
|--------|-----------|
| `KEYSTORE_BASE64` | O arquivo `.jks` ou `.p12` do keystore, codificado em Base64 |
| `KEYSTORE_PASSWORD` | Senha do keystore |
| `KEY_ALIAS` | Alias da chave dentro do keystore |
| `KEY_PASSWORD` | Senha da chave (geralmente igual à do keystore) |

## Como gerar o KEYSTORE_BASE64

### Opção 1 — A partir do Signed APK Wizard (dentro do app)
1. Abra o app no seu dispositivo
2. No menu `⋮ > Signed APK Wizard`
3. Crie um novo keystore
4. O arquivo `.p12` ficará salvo em `files/keystores/`
5. No terminal: `base64 -i meu-keystore.p12 | pbcopy` (macOS) ou `base64 meu-keystore.p12 | xclip` (Linux)

### Opção 2 — Via keytool no terminal
```bash
keytool -genkey -v \
  -keystore release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 9125 \
  -alias mykey \
  -storepass minha-senha \
  -keypass minha-senha \
  -dname "CN=Meu App,O=Minha Empresa,C=BR"

# Codificar em Base64
base64 release.jks > release.jks.b64
# Cole o conteúdo de release.jks.b64 no secret KEYSTORE_BASE64
```

## Como criar uma Release

1. Faça commit de todas as alterações
2. Crie uma tag de versão:
   ```bash
   git tag v2.1
   git push origin v2.1
   ```
3. O GitHub Actions irá automaticamente:
   - Compilar o APK
   - Assinar com o keystore dos secrets
   - Criar uma GitHub Release com o APK assinado

## Estrutura do workflow

```
build.yml
├── job: build
│   ├── assembleDebug
│   ├── test (opcional)
│   ├── lintDebug (opcional)
│   ├── assembleRelease (unsigned)
│   └── upload artifacts
└── job: sign (somente em tags v* ou workflow_dispatch)
    ├── decodificar keystore dos secrets
    ├── assembleRelease com signing config
    ├── verificar assinatura (apksigner)
    ├── upload APK assinado
    └── criar GitHub Release
```
