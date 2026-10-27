# DGT2816 — Interação com sensores de smartphones e wearables

Trabalho prático da disciplina **DGT2816 — Interação com sensores de smartphones e
wearables**.

## Evidências

- **Vídeo de demonstração:** [`docs/demo-doma-assist.mp4`](docs/demo-doma-assist.mp4)
  — o Doma Assist em funcionamento: mensagem chegando, pergunta
  *"Nova mensagem. Deseja ouvir?"*, resposta por voz e leitura em voz alta.
- **Capturas de tela:** [`docs/capturas/`](docs/capturas) — emulador Wear OS,
  Doma Assist e ListaDeTarefas rodando, Device Manager.
- **Documentação em PDF:**
  - [`docs/documentacao-trabalho-pratico.pdf`](docs/documentacao-trabalho-pratico.pdf)
    — documento do trabalho prático (requisitos, arquitetura, como testar)
  - [`docs/documentacao-microatividades.pdf`](docs/documentacao-microatividades.pdf)
    — evidências das microatividades 1–5

## Os dois projetos deste repositório

| | [`ListaDeTarefas/`](ListaDeTarefas) | [`Doma/`](Doma) |
|---|---|---|
| **Papel** | Microatividades 2–4 do roteiro | Trabalho prático (entrega principal) |
| **O que é** | App simples de lista de tarefas | App de assistência por áudio para funcionários com necessidades especiais |
| **Interação** | Visual: `ListView` + `Button` | Auditiva: voz (TTS + reconhecimento), toque duplo, gesto de agitar |
| **Recursos** | Adicionar tarefas a uma lista | Leitura de mensagens com confirmação por voz, comandos falados, alertas de segurança, detecção de saídas de áudio/Bluetooth, Tile e complication no mostrador |
| **Sensores** | Permissões `BODY_SENSORS`/`WAKE_LOCK` no manifest | Acelerômetro (shake detecta o gesto), dispositivos de áudio |

Resumindo: o `ListaDeTarefas` é o exercício de aquecimento que o roteiro
pede nas microatividades; o `Doma` é o produto final da disciplina.

## Como executar

1. Android Studio → **Open** → pasta do projeto → aguarde o **Gradle Sync**.
2. **Tools > Device Manager** → crie um dispositivo **Wear OS**
   (ex.: *Wear OS Small Round*, API 30+).
3. Selecione o emulador e clique em **Run** (▶).
4. Para testar a voz no emulador: *Extended Controls → Microphone → Enable
   Host Microphone Access* e permita o microfone quando o app pedir.
