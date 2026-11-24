<div align="center">
  <img src="docs/slate.svg" width="104" height="104" alt="Ícone do Slate">

  <h1>Slate</h1>

  <p><strong>Gerenciamento de armazenamento, limpeza e diagnóstico do dispositivo para Android.</strong></p>

  <p><a href="https://github.com/stevenmx2/Slate/releases/latest"><strong>Baixar APK</strong></a> · Android 8.0+ · GPL-3.0-only · <a href="README.md">English</a></p>
</div>

O Slate reúne análise de armazenamento, gerenciamento de aplicativos e informações do dispositivo em uma interface Material 3. Descubra o que ocupa espaço, revise arquivos desnecessários e consulte informações de hardware.

## Recursos

- **Armazenamento:** memória interna e removível, lista e grade, pesquisa, ordenação, categorias, maiores arquivos, índice persistente e histórico de análises.
- **Limpeza:** arquivos duplicados por conteúdo, temporários, pastas vazias, possíveis resíduos de apps desinstalados, exclusões personalizadas e análises agendadas.
- **Aplicativos:** tamanho do app, dados e cache, filtros de apps do usuário e do sistema, detalhes, pastas acessíveis, abertura e desinstalação pelo Android.
- **Dispositivo:** CPU, GPU, RAM, bateria, potência de carregamento, sistema, rede, tela, temperaturas, sensores em tempo real e testes de tela e toque.
- **Acesso avançado:** análise e limpeza seletiva do cache via Shizuku (ADB ou root) e revisão de resíduos protegidos, quando o acesso estiver disponível.
- **Interface:** temas claro, escuro e AMOLED, cores dinâmicas e português brasileiro.

## Ferramentas adicionais

Gerenciador de arquivos com abas, favoritos e operações em lote; galeria offline com visualização de fotos e vídeos em tela cheia; monitor de FPS e pacote via Shizuku.

## Instalação

Baixe o arquivo **[Slate.apk nas Releases do GitHub](https://github.com/stevenmx2/Slate/releases/latest)**, abra-o e autorize a instalação, caso o Android solicite.

Requer Android 8.0 ou superior. Root e Shizuku são opcionais. O Slate está em desenvolvimento ativo; os APKs publicados são builds de desenvolvimento assinadas.

O atualizador integrado consulta as releases e verifica o pacote, a versão e a assinatura antes de abrir o instalador do Android.

## Privacidade e permissões

As análises, índices, informações de apps e medições do aparelho são processadas localmente. O acesso ao armazenamento e às estatísticas de uso é solicitado conforme a função. A conexão com a internet é usada para atualizações.

A limpeza exige revisão antes da exclusão; análises agendadas não apagam automaticamente arquivos pessoais. O acesso a arquivos privados depende das permissões e restrições do Android. Os totais de apps e arquivos compartilhados podem se sobrepor.

## Desenvolvimento

Código escrito em Kotlin com Jetpack Compose e Material 3. Consulte o [README em inglês](README.md#development) para compilar e o [changelog](CHANGELOG.md) e o [roteiro de desenvolvimento](ROADMAP.md) para acompanhar a validação dos recursos.

Relate problemas em [Issues](https://github.com/stevenmx2/Slate/issues).

## Licença e créditos

O Slate é software livre sob a licença [GPL-3.0-only](LICENSE). O SD Maid SE é uma referência de funcionalidades e compatibilidade. Consulte [CREDITS.md](CREDITS.md) para os créditos e as licenças das dependências.
