package com.example.domain.model

/**
 * Full A-Z OpenCode provider catalog (115 listed providers + Ollama Local = 116 total).
 */
object ProviderCatalog {

    val all: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "302ai",
            title = "302.AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.302.ai/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Pay-as-you-go multi-model AI gateway."
        ),
        ProviderPreset(
            id = "abacus",
            title = "Abacus",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://routellm.abacus.ai/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Abacus RouteLLM OpenAI-compatible endpoint."
        ),
        ProviderPreset(
            id = "aihubmix",
            title = "AIHubMix",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://aihubmix.com/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Unified multi-provider API aggregator."
        ),
        ProviderPreset(
            id = "alibaba",
            title = "Alibaba",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Alibaba Cloud Model Studio (International / DashScope)."
        ),
        ProviderPreset(
            id = "alibaba-cn",
            title = "Alibaba (China)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Alibaba Cloud DashScope Mainland China endpoint."
        ),
        ProviderPreset(
            id = "alibaba-coding-plan",
            title = "Alibaba Coding Plan",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://coding-intl.dashscope.aliyuncs.com/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Alibaba International Coding Plan endpoint (Qwen Coder)."
        ),
        ProviderPreset(
            id = "alibaba-coding-plan-cn",
            title = "Alibaba Coding Plan (China)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://coding.dashscope.aliyuncs.com/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Alibaba Mainland China Coding Plan endpoint."
        ),
        ProviderPreset(
            id = "amazon-bedrock",
            title = "Amazon Bedrock",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://bedrock-runtime.<region>.amazonaws.com/openai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Replace <region> (e.g. us-east-1) or point Base URL to your Bedrock Access Gateway / LiteLLM proxy."
        ),
        ProviderPreset(
            id = "anthropic",
            title = "Anthropic",
            type = ProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com",
            category = ProviderCategory.POPULAR,
            isPopular = true,
            helperHint = "Uses /v1/messages with x-api-key and anthropic-version headers."
        ),
        ProviderPreset(
            id = "azure",
            title = "Azure",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://<resource-name>.openai.azure.com/openai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Replace <resource-name> with your Azure OpenAI resource name."
        ),
        ProviderPreset(
            id = "azure-cognitive-services",
            title = "Azure Cognitive Services",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://<resource-name>.cognitiveservices.azure.com/openai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Replace <resource-name> with your Azure AI Foundry / Cognitive Services resource."
        ),
        ProviderPreset(
            id = "bailing",
            title = "Bailing",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.tbox.cn/api/llm/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "InclusionAI / Bailing OpenAI-compatible endpoint."
        ),
        ProviderPreset(
            id = "baseten",
            title = "Baseten",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://inference.baseten.co/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Baseten Model APIs or custom deployment URL."
        ),
        ProviderPreset(
            id = "berget-ai",
            title = "Berget.AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.berget.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "European sovereign AI inference endpoint."
        ),
        ProviderPreset(
            id = "cerebras",
            title = "Cerebras",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.cerebras.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            isPopular = true,
            helperHint = "Wafer-scale ultra-fast OpenAI-compatible inference."
        ),
        ProviderPreset(
            id = "chutes",
            title = "Chutes",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://llm.chutes.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Serverless decentralized GPU inference."
        ),
        ProviderPreset(
            id = "clarifai",
            title = "Clarifai",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.clarifai.com/v2/ext/openai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Use your Clarifai PAT as the API key."
        ),
        ProviderPreset(
            id = "cloudferro-sherlock",
            title = "CloudFerro Sherlock",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api-sherlock.cloudferro.com/openai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "European GDPR-compliant managed GenAI endpoint."
        ),
        ProviderPreset(
            id = "cloudflare-ai-gateway",
            title = "Cloudflare AI Gateway",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://gateway.ai.cloudflare.com/v1/<account-id>/<gateway-id>/compat",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Replace <account-id> and <gateway-id> with your Cloudflare AI Gateway IDs."
        ),
        ProviderPreset(
            id = "cloudflare-workers-ai",
            title = "Cloudflare Workers AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.cloudflare.com/client/v4/accounts/<account-id>/ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Replace <account-id> and use a Cloudflare Workers AI API token."
        ),
        ProviderPreset(
            id = "cohere",
            title = "Cohere",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.cohere.ai/compatibility/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Cohere OpenAI-compatibility endpoint for Command models."
        ),
        ProviderPreset(
            id = "cortecs",
            title = "Cortecs",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.cortecs.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "European high-throughput inference API."
        ),
        ProviderPreset(
            id = "custom",
            title = "Custom",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://<your-host>/v1",
            category = ProviderCategory.LOCAL,
            isPopular = true,
            helperHint = "Connect any custom OpenAI-compatible, Anthropic, or Gemini endpoint."
        ),
        ProviderPreset(
            id = "d-run-cn",
            title = "D.Run (China)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://chat.d.run/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "D.Run MaaS endpoint (or https://<region>.d.run/v1 for dedicated instances)."
        ),
        ProviderPreset(
            id = "deep-infra",
            title = "Deep Infra",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.deepinfra.com/v1/openai",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Serverless open-weights inference API."
        ),
        ProviderPreset(
            id = "deepseek",
            title = "DeepSeek",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.deepseek.com/v1",
            category = ProviderCategory.POPULAR,
            isPopular = true,
            helperHint = "DeepSeek-V3 and DeepSeek-R1 reasoning models."
        ),
        ProviderPreset(
            id = "digitalocean",
            title = "DigitalOcean",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://inference.do-ai.run/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "DigitalOcean Gradient / Serverless Inference using a Model Access Key."
        ),
        ProviderPreset(
            id = "dinference",
            title = "Dinference",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.dinference.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Distributed OpenAI-compatible inference gateway."
        ),
        ProviderPreset(
            id = "evroc",
            title = "evroc",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://models.think.evroc.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "European sovereign cloud AI (evroc Think)."
        ),
        ProviderPreset(
            id = "fastrouter",
            title = "FastRouter",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://go.fastrouter.ai/api/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Unified multi-model routing API."
        ),
        ProviderPreset(
            id = "fireworks-ai",
            title = "Fireworks AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.fireworks.ai/inference/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Low-latency serverless and fine-tuned model hosting."
        ),
        ProviderPreset(
            id = "firmware",
            title = "Firmware",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://app.firmware.ai/api/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Firmware AI unified gateway endpoint."
        ),
        ProviderPreset(
            id = "friendli",
            title = "Friendli",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.friendli.ai/serverless/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "FriendliAI serverless inference endpoint."
        ),
        ProviderPreset(
            id = "github-copilot",
            title = "GitHub Copilot",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.githubcopilot.com",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Requires a GitHub Copilot session token or local Copilot proxy."
        ),
        ProviderPreset(
            id = "github-models",
            title = "GitHub Models",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://models.github.ai/inference",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Authenticate with a GitHub Personal Access Token (models:read)."
        ),
        ProviderPreset(
            id = "gitlab-duo",
            title = "GitLab Duo",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://cloud.gitlab.com/ai/v1/proxy/openai/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "GitLab Duo AI gateway (customize host for self-managed GitLab)."
        ),
        ProviderPreset(
            id = "google",
            title = "Google",
            type = ProviderType.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            category = ProviderCategory.POPULAR,
            isPopular = true,
            helperHint = "Use an AI Studio Gemini API key (AIza...) for Gemini, Imagen, and Veo."
        ),
        ProviderPreset(
            id = "groq",
            title = "Groq",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.groq.com/openai/v1",
            category = ProviderCategory.POPULAR,
            isPopular = true,
            helperHint = "Ultra-fast LPU inference for Llama, Qwen, and Gemma."
        ),
        ProviderPreset(
            id = "helicone",
            title = "Helicone",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://ai-gateway.helicone.ai/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Helicone AI Gateway with observability and routing."
        ),
        ProviderPreset(
            id = "hpc-ai",
            title = "HPC-AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.hpcaitech.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "HPC-AI Tech cloud inference API."
        ),
        ProviderPreset(
            id = "hugging-face",
            title = "Hugging Face",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://router.huggingface.co/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Hugging Face Inference Providers router (use HF access token)."
        ),
        ProviderPreset(
            id = "iflow",
            title = "iFlow",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://apis.iflow.cn/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "iFlow OpenAI-compatible API endpoint."
        ),
        ProviderPreset(
            id = "inception",
            title = "Inception",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.inceptionlabs.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Inception Labs diffusion LLM API (Mercury Coder)."
        ),
        ProviderPreset(
            id = "inference",
            title = "Inference",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://inference.net/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Inference.net OpenAI-compatible serverless API."
        ),
        ProviderPreset(
            id = "io-net",
            title = "IO.NET",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.intelligence.io.solutions/api/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "IO Intelligence decentralized inference endpoint."
        ),
        ProviderPreset(
            id = "jiekou-ai",
            title = "Jiekou.AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.jiekou.ai/openai/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Jiekou.AI multi-model OpenAI-compatible gateway."
        ),
        ProviderPreset(
            id = "kilo-gateway",
            title = "Kilo Gateway",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.kilo.ai/api/gateway",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Kilo Code unified model gateway."
        ),
        ProviderPreset(
            id = "kimi-for-coding",
            title = "Kimi For Coding",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.kimi.com/coding/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Moonshot Kimi Coding Plan (switch to Anthropic protocol for /coding/ if preferred)."
        ),
        ProviderPreset(
            id = "kuae-cloud-coding-plan",
            title = "KUAE Cloud Coding Plan",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://coding-plan-endpoint.kuaecloud.net/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "KUAE Cloud Coding Plan OpenAI-compatible endpoint."
        ),
        ProviderPreset(
            id = "llama-llm-gateway",
            title = "Llama (LLM Gateway)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.llama.com/compat/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Meta Llama API OpenAI-compatible gateway."
        ),
        ProviderPreset(
            id = "lmstudio",
            title = "LMStudio",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "http://10.0.2.2:1234/v1",
            category = ProviderCategory.LOCAL,
            isPopular = true,
            requiresApiKey = false,
            helperHint = "Local LM Studio server (10.0.2.2:1234 on emulator or your LAN IP)."
        ),
        ProviderPreset(
            id = "lucidquery-ai",
            title = "LucidQuery AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://lucidquery.com/api/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "LucidQuery AI inference gateway."
        ),
        ProviderPreset(
            id = "meganova",
            title = "Meganova",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.meganova.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Meganova AI OpenAI-compatible API."
        ),
        ProviderPreset(
            id = "minimax-io",
            title = "MiniMax (minimax.io)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.minimax.io/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "MiniMax Global API endpoint."
        ),
        ProviderPreset(
            id = "minimax-cn",
            title = "MiniMax (minimaxi.com)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.minimaxi.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "MiniMax Mainland China API endpoint."
        ),
        ProviderPreset(
            id = "minimax-coding-plan-io",
            title = "MiniMax Coding Plan (minimax.io)",
            type = ProviderType.ANTHROPIC,
            baseUrl = "https://api.minimax.io/anthropic",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "MiniMax Global Coding Plan (Anthropic-compatible endpoint)."
        ),
        ProviderPreset(
            id = "minimax-coding-plan-cn",
            title = "MiniMax Coding Plan (minimaxi.com)",
            type = ProviderType.ANTHROPIC,
            baseUrl = "https://api.minimaxi.com/anthropic",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "MiniMax China Coding Plan (Anthropic-compatible endpoint)."
        ),
        ProviderPreset(
            id = "mistral",
            title = "Mistral",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.mistral.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            isPopular = true,
            helperHint = "Mistral Large, Small, Magistral, and Codestral models."
        ),
        ProviderPreset(
            id = "mixlayer",
            title = "Mixlayer",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://models.mixlayer.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Mixlayer serverless inference endpoint."
        ),
        ProviderPreset(
            id = "moark",
            title = "Moark",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://moark.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Gitee AI / Moark OpenAI-compatible API."
        ),
        ProviderPreset(
            id = "modelscope",
            title = "ModelScope",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api-inference.modelscope.cn/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "ModelScope API-Inference endpoint."
        ),
        ProviderPreset(
            id = "moonshot-ai",
            title = "Moonshot AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.moonshot.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Moonshot AI Global (Kimi models)."
        ),
        ProviderPreset(
            id = "moonshot-ai-cn",
            title = "Moonshot AI (China)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.moonshot.cn/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Moonshot AI Mainland China (Kimi models)."
        ),
        ProviderPreset(
            id = "morph",
            title = "Morph",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.morphllm.com/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Morph Fast Apply and coding inference API."
        ),
        ProviderPreset(
            id = "nanogpt",
            title = "NanoGPT",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://nano-gpt.com/api/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "NanoGPT pay-per-prompt multi-model gateway."
        ),
        ProviderPreset(
            id = "nebius-token-factory",
            title = "Nebius Token Factory",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.studio.nebius.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Nebius AI Studio / Token Factory endpoint."
        ),
        ProviderPreset(
            id = "nova",
            title = "Nova",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.nova.amazon.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Amazon Nova direct OpenAI-compatible API."
        ),
        ProviderPreset(
            id = "novitaai",
            title = "NovitaAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.novita.ai/v3/openai",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Novita AI serverless open-source LLM & media inference."
        ),
        ProviderPreset(
            id = "nvidia",
            title = "Nvidia",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://integrate.api.nvidia.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "NVIDIA NIM API (nvapi-... key)."
        ),
        ProviderPreset(
            id = "ollama-local",
            title = "Ollama (Local)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "http://10.0.2.2:11434/v1",
            category = ProviderCategory.LOCAL,
            isPopular = true,
            requiresApiKey = false,
            helperHint = "Local Ollama server (10.0.2.2:11434 on emulator or your LAN IP)."
        ),
        ProviderPreset(
            id = "ollama-cloud",
            title = "Ollama Cloud",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://ollama.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Ollama Cloud hosted models using an Ollama API key."
        ),
        ProviderPreset(
            id = "openai",
            title = "OpenAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com/v1",
            category = ProviderCategory.POPULAR,
            isPopular = true,
            helperHint = "Supports GPT chat, vision, and image generation."
        ),
        ProviderPreset(
            id = "opencode-go",
            title = "OpenCode Go",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://opencode.ai/zen/go/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "OpenCode Go subscription gateway."
        ),
        ProviderPreset(
            id = "opencode-zen",
            title = "OpenCode Zen",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://opencode.ai/zen/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "OpenCode Zen curated model gateway."
        ),
        ProviderPreset(
            id = "openrouter",
            title = "OpenRouter",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            category = ProviderCategory.POPULAR,
            isPopular = true,
            helperHint = "Access hundreds of models across labs with one key."
        ),
        ProviderPreset(
            id = "ovhcloud-ai-endpoints",
            title = "OVHcloud AI Endpoints",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "OVHcloud European sovereign AI Endpoints."
        ),
        ProviderPreset(
            id = "perplexity",
            title = "Perplexity",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.perplexity.ai",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Perplexity Sonar search-grounded models."
        ),
        ProviderPreset(
            id = "perplexity-agent",
            title = "Perplexity Agent",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.perplexity.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Perplexity Agent / research API endpoint."
        ),
        ProviderPreset(
            id = "poe",
            title = "Poe",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.poe.com/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Poe OpenAI-compatible API endpoint."
        ),
        ProviderPreset(
            id = "privatemode-ai",
            title = "Privatemode AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "http://10.0.2.2:8080/v1",
            category = ProviderCategory.LOCAL,
            helperHint = "Confidential AI proxy (point to your Privatemode proxy host/port)."
        ),
        ProviderPreset(
            id = "qihang",
            title = "QiHang",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.qihang.ai/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "QiHang AI OpenAI-compatible gateway."
        ),
        ProviderPreset(
            id = "qiniu",
            title = "Qiniu",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.qnaigc.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Qiniu Cloud AI Inference endpoint."
        ),
        ProviderPreset(
            id = "regolo-ai",
            title = "Regolo AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.regolo.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "European sustainable GPU inference API."
        ),
        ProviderPreset(
            id = "requesty",
            title = "Requesty",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://router.requesty.ai/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Requesty smart multi-model LLM router."
        ),
        ProviderPreset(
            id = "sap-ai-core",
            title = "SAP AI Core",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://<api-url>/v2/inference/deployments/<deployment-id>",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Replace <api-url> & <deployment-id>, or point to your SAP AI Core OpenAI proxy."
        ),
        ProviderPreset(
            id = "scaleway",
            title = "Scaleway",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.scaleway.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Scaleway Generative APIs (France / EU)."
        ),
        ProviderPreset(
            id = "siliconflow",
            title = "SiliconFlow",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.siliconflow.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "SiliconFlow International serverless API."
        ),
        ProviderPreset(
            id = "siliconflow-cn",
            title = "SiliconFlow (China)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.siliconflow.cn/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "SiliconFlow Mainland China serverless API."
        ),
        ProviderPreset(
            id = "stackit",
            title = "STACKIT",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai-compat.model-serving.eu01.onstackit.cloud/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "STACKIT European Model Serving OpenAI-compatible API."
        ),
        ProviderPreset(
            id = "stepfun",
            title = "StepFun",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.stepfun.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "StepFun multimodal and reasoning models."
        ),
        ProviderPreset(
            id = "submodel",
            title = "submodel",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://llm.submodel.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "submodel serverless inference API."
        ),
        ProviderPreset(
            id = "synthetic",
            title = "Synthetic",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.synthetic.new/openai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Synthetic privacy-first open-weights inference."
        ),
        ProviderPreset(
            id = "tencent-coding-plan-cn",
            title = "Tencent Coding Plan (China)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.lkeap.cloud.tencent.com/coding/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Tencent Cloud Coding Plan Mainland China endpoint."
        ),
        ProviderPreset(
            id = "tencent-tokenhub",
            title = "Tencent TokenHub",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://tokenhub.tencentmaas.com/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Tencent Cloud TokenHub unified model gateway."
        ),
        ProviderPreset(
            id = "the-grid-ai",
            title = "The Grid AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.thegrid.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "The Grid AI OpenAI-compatible inference endpoint."
        ),
        ProviderPreset(
            id = "together-ai",
            title = "Together AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.together.xyz/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            isPopular = true,
            helperHint = "Open-weights chat and FLUX image generation."
        ),
        ProviderPreset(
            id = "upstage",
            title = "Upstage",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.upstage.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Upstage Solar Pro & document AI models."
        ),
        ProviderPreset(
            id = "v0",
            title = "v0",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.v0.dev/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Vercel v0 Model API (OpenAI-compatible)."
        ),
        ProviderPreset(
            id = "venice-ai",
            title = "Venice AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.venice.ai/api/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Privacy-focused uncensored open-weights inference."
        ),
        ProviderPreset(
            id = "vercel-ai-gateway",
            title = "Vercel AI Gateway",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://ai-gateway.vercel.sh/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Unified routing across providers via Vercel AI Gateway key."
        ),
        ProviderPreset(
            id = "vertex",
            title = "Vertex",
            type = ProviderType.GEMINI,
            baseUrl = "https://<region>-aiplatform.googleapis.com/v1/projects/<project-id>/locations/<region>/publishers/google",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Replace <region> & <project-id> (use an OAuth bearer proxy or switch to OpenAI-Compatible for Vertex OpenAI endpoint)."
        ),
        ProviderPreset(
            id = "vertex-anthropic",
            title = "Vertex (Anthropic)",
            type = ProviderType.ANTHROPIC,
            baseUrl = "https://<region>-aiplatform.googleapis.com/v1/projects/<project-id>/locations/<region>/publishers/anthropic",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Replace <region> & <project-id> or configure a Vertex Anthropic gateway proxy."
        ),
        ProviderPreset(
            id = "vivgrid",
            title = "Vivgrid",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.vivgrid.com/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "Vivgrid AI orchestration gateway."
        ),
        ProviderPreset(
            id = "vultr",
            title = "Vultr",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.vultrinference.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Vultr Serverless Inference API."
        ),
        ProviderPreset(
            id = "wafer",
            title = "Wafer",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.wafer.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Wafer AI high-speed inference endpoint."
        ),
        ProviderPreset(
            id = "weights-and-biases",
            title = "Weights & Biases",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.inference.wandb.ai/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "W&B Inference OpenAI-compatible API."
        ),
        ProviderPreset(
            id = "xai",
            title = "xAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.x.ai/v1",
            category = ProviderCategory.POPULAR,
            isPopular = true,
            helperHint = "Grok models via xAI API."
        ),
        ProviderPreset(
            id = "xiaomi",
            title = "Xiaomi",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.xiaomimimo.com/v1",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Xiaomi MiMo OpenAI-compatible API."
        ),
        ProviderPreset(
            id = "xiaomi-token-plan-cn",
            title = "Xiaomi Token Plan (China)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://token-plan-cn.xiaomimimo.com/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Xiaomi MiMo Token Plan (Mainland China)."
        ),
        ProviderPreset(
            id = "xiaomi-token-plan-eu",
            title = "Xiaomi Token Plan (Europe)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://token-plan-ams.xiaomimimo.com/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Xiaomi MiMo Token Plan (Europe / Amsterdam)."
        ),
        ProviderPreset(
            id = "xiaomi-token-plan-sg",
            title = "Xiaomi Token Plan (Singapore)",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://token-plan-sgp.xiaomimimo.com/v1",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Xiaomi MiMo Token Plan (Singapore)."
        ),
        ProviderPreset(
            id = "z-ai",
            title = "Z.AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.z.ai/api/paas/v4",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Z.AI Global GLM API endpoint."
        ),
        ProviderPreset(
            id = "z-ai-coding-plan",
            title = "Z.AI Coding Plan",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.z.ai/api/coding/paas/v4",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Z.AI Global Coding Plan endpoint for GLM coding models."
        ),
        ProviderPreset(
            id = "zenmux",
            title = "ZenMux",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://zenmux.ai/api/v1",
            category = ProviderCategory.GATEWAYS,
            helperHint = "ZenMux multi-provider routing gateway."
        ),
        ProviderPreset(
            id = "zhipu-ai",
            title = "Zhipu AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            category = ProviderCategory.CLOUD_ENTERPRISE,
            helperHint = "Zhipu AI BigModel (China) GLM endpoint."
        ),
        ProviderPreset(
            id = "zhipu-ai-coding-plan",
            title = "Zhipu AI Coding Plan",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://open.bigmodel.cn/api/coding/paas/v4",
            category = ProviderCategory.CODING_PLANS,
            helperHint = "Zhipu AI China Coding Plan endpoint."
        )
    ).sortedBy { it.title.lowercase() }

    val popular: List<ProviderPreset> = listOf(
        "google",
        "openai",
        "anthropic",
        "openrouter",
        "groq",
        "deepseek",
        "xai",
        "mistral",
        "together-ai",
        "cerebras",
        "ollama-local",
        "lmstudio",
        "custom"
    ).mapNotNull { id -> all.firstOrNull { it.id == id } }

    val defaultPreset: ProviderPreset = all.first { it.id == "google" }

    val customPreset: ProviderPreset = all.first { it.id == "custom" }

    private val apiKeyUrls: Map<String, String> = mapOf(
        "302ai" to "https://302.ai",
        "abacus" to "https://abacus.ai",
        "aihubmix" to "https://aihubmix.com",
        "alibaba" to "https://modelstudio.console.alibabacloud.com",
        "alibaba-cn" to "https://bailian.console.aliyun.com",
        "alibaba-coding-plan" to "https://modelstudio.console.alibabacloud.com",
        "alibaba-coding-plan-cn" to "https://bailian.console.aliyun.com",
        "amazon-bedrock" to "https://console.aws.amazon.com/bedrock/",
        "anthropic" to "https://console.anthropic.com/settings/keys",
        "azure" to "https://ai.azure.com",
        "azure-cognitive-services" to "https://ai.azure.com",
        "bailing" to "https://www.tbox.cn",
        "baseten" to "https://app.baseten.co/settings/api_keys",
        "berget-ai" to "https://berget.ai",
        "cerebras" to "https://cloud.cerebras.ai",
        "chutes" to "https://chutes.ai",
        "clarifai" to "https://clarifai.com/settings/security",
        "cloudferro-sherlock" to "https://cloudferro.com",
        "cloudflare-ai-gateway" to "https://dash.cloudflare.com",
        "cloudflare-workers-ai" to "https://dash.cloudflare.com",
        "cohere" to "https://dashboard.cohere.com/api-keys",
        "cortecs" to "https://cortecs.ai",
        "d-run-cn" to "https://d.run",
        "deep-infra" to "https://deepinfra.com/dash/api_keys",
        "deepseek" to "https://platform.deepseek.com/api_keys",
        "digitalocean" to "https://cloud.digitalocean.com/gen-ai",
        "dinference" to "https://dinference.com",
        "evroc" to "https://evroc.com",
        "fastrouter" to "https://fastrouter.ai",
        "fireworks-ai" to "https://fireworks.ai/account/api-keys",
        "firmware" to "https://firmware.ai",
        "friendli" to "https://suite.friendli.ai",
        "github-copilot" to "https://github.com/features/copilot",
        "github-models" to "https://github.com/marketplace/models",
        "gitlab-duo" to "https://about.gitlab.com/gitlab-duo/",
        "google" to "https://aistudio.google.com/apikey",
        "groq" to "https://console.groq.com/keys",
        "helicone" to "https://us.helicone.ai/settings/api-keys",
        "hpc-ai" to "https://hpcaitech.com",
        "hugging-face" to "https://huggingface.co/settings/tokens",
        "iflow" to "https://iflow.cn",
        "inception" to "https://platform.inceptionlabs.ai",
        "inference" to "https://inference.net",
        "io-net" to "https://intelligence.io.solutions",
        "jiekou-ai" to "https://jiekou.ai",
        "kilo-gateway" to "https://kilo.ai",
        "kimi-for-coding" to "https://www.kimi.com",
        "kuae-cloud-coding-plan" to "https://kuaecloud.net",
        "llama-llm-gateway" to "https://llama.developer.meta.com",
        "lmstudio" to "https://lmstudio.ai/docs/app/api",
        "lucidquery-ai" to "https://lucidquery.com",
        "meganova" to "https://meganova.ai",
        "minimax-io" to "https://platform.minimax.io",
        "minimax-cn" to "https://platform.minimaxi.com",
        "minimax-coding-plan-io" to "https://platform.minimax.io",
        "minimax-coding-plan-cn" to "https://platform.minimaxi.com",
        "mistral" to "https://console.mistral.ai/api-keys",
        "mixlayer" to "https://mixlayer.ai",
        "moark" to "https://moark.com",
        "modelscope" to "https://modelscope.cn/my/myaccesstoken",
        "moonshot-ai" to "https://platform.moonshot.ai/console/api-keys",
        "moonshot-ai-cn" to "https://platform.moonshot.cn/console/api-keys",
        "morph" to "https://morphllm.com",
        "nanogpt" to "https://nano-gpt.com/api",
        "nebius-token-factory" to "https://studio.nebius.com/settings/api-keys",
        "nova" to "https://nova.amazon.com/dev/api",
        "novitaai" to "https://novita.ai/settings/key-management",
        "nvidia" to "https://build.nvidia.com/explore/discover",
        "ollama-local" to "https://ollama.com",
        "ollama-cloud" to "https://ollama.com/settings/keys",
        "openai" to "https://platform.openai.com/api-keys",
        "opencode-go" to "https://opencode.ai",
        "opencode-zen" to "https://opencode.ai",
        "openrouter" to "https://openrouter.ai/keys",
        "ovhcloud-ai-endpoints" to "https://endpoints.ai.cloud.ovh.net",
        "perplexity" to "https://www.perplexity.ai/settings/api",
        "perplexity-agent" to "https://www.perplexity.ai/settings/api",
        "poe" to "https://poe.com/api_key",
        "privatemode-ai" to "https://privatemode.ai",
        "qihang" to "https://qihang.ai",
        "qiniu" to "https://portal.qiniu.com",
        "regolo-ai" to "https://regolo.ai",
        "requesty" to "https://app.requesty.ai/api-keys",
        "sap-ai-core" to "https://discovery-center.cloud.sap/serviceCatalog/sap-ai-core",
        "scaleway" to "https://console.scaleway.com/generative-apis",
        "siliconflow" to "https://cloud.siliconflow.com/account/ak",
        "siliconflow-cn" to "https://cloud.siliconflow.cn/account/ak",
        "stackit" to "https://www.stackit.de",
        "stepfun" to "https://platform.stepfun.com/interface-key",
        "submodel" to "https://submodel.ai",
        "synthetic" to "https://synthetic.new",
        "tencent-coding-plan-cn" to "https://console.cloud.tencent.com/lkeap",
        "tencent-tokenhub" to "https://console.cloud.tencent.com/lkeap",
        "the-grid-ai" to "https://thegrid.ai",
        "together-ai" to "https://api.together.xyz/settings/api-keys",
        "upstage" to "https://console.upstage.ai/api-keys",
        "v0" to "https://v0.dev/chat/settings/keys",
        "venice-ai" to "https://venice.ai/settings/api",
        "vercel-ai-gateway" to "https://vercel.com/ai-gateway",
        "vertex" to "https://console.cloud.google.com/vertex-ai",
        "vertex-anthropic" to "https://console.cloud.google.com/vertex-ai",
        "vivgrid" to "https://vivgrid.com",
        "vultr" to "https://my.vultr.com/settings/#settingsapi",
        "wafer" to "https://wafer.ai",
        "weights-and-biases" to "https://wandb.ai/authorize",
        "xai" to "https://console.x.ai",
        "xiaomi" to "https://platform.xiaomimimo.com",
        "xiaomi-token-plan-cn" to "https://platform.xiaomimimo.com",
        "xiaomi-token-plan-eu" to "https://platform.xiaomimimo.com",
        "xiaomi-token-plan-sg" to "https://platform.xiaomimimo.com",
        "z-ai" to "https://z.ai",
        "z-ai-coding-plan" to "https://z.ai",
        "zenmux" to "https://zenmux.ai",
        "zhipu-ai" to "https://open.bigmodel.cn/usercenter/apikeys",
        "zhipu-ai-coding-plan" to "https://open.bigmodel.cn/usercenter/apikeys"
    )

    fun resolveWebsiteUrl(name: String, baseUrl: String): String? {
        val cleanName = name.trim()
        val cleanBase = baseUrl.trim().trimEnd('/')

        val matchedPreset = all.firstOrNull { preset ->
            cleanName.isNotEmpty() && preset.title.equals(cleanName, ignoreCase = true)
        } ?: all.firstOrNull { preset ->
            cleanBase.isNotEmpty() && preset.baseUrl.trimEnd('/').equals(cleanBase, ignoreCase = true)
        } ?: all.firstOrNull { preset ->
            preset.id != "custom" && cleanName.isNotEmpty() &&
                (cleanName.contains(preset.title, ignoreCase = true) || preset.title.contains(cleanName, ignoreCase = true))
        }

        matchedPreset?.let { preset ->
            apiKeyUrls[preset.id]?.let { return it }
        }

        if (cleanBase.isNotEmpty() && !cleanBase.contains('<') && !cleanBase.contains('>')) {
            val host = runCatching { java.net.URI(cleanBase).host }.getOrNull()?.lowercase()
            if (!host.isNullOrBlank() &&
                host != "localhost" &&
                host != "127.0.0.1" &&
                host != "10.0.2.2"
            ) {
                val stripped = host
                    .removePrefix("api.")
                    .removePrefix("inference.")
                    .removePrefix("models.")
                    .removePrefix("router.")
                    .removePrefix("llm.")
                return "https://$stripped"
            }
        }
        return null
    }

    fun filter(query: String, category: ProviderCategory): List<ProviderPreset> {
        val q = query.trim()
        val byCategory = when (category) {
            ProviderCategory.ALL -> all
            ProviderCategory.POPULAR -> popular
            else -> all.filter { it.category == category }
        }
        if (q.isEmpty()) return byCategory
        return byCategory.filter { preset ->
            preset.title.contains(q, ignoreCase = true) ||
                preset.baseUrl.contains(q, ignoreCase = true) ||
                preset.type.label.contains(q, ignoreCase = true) ||
                preset.helperHint.contains(q, ignoreCase = true)
        }
    }
}
