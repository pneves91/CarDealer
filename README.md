# CarDealer API

API REST desenvolvida em Java com Spring Boot, para gestão de stands de automóveis.  
Permite a autenticação de utilizadores, gestão de contas, registo de empresas (stands), e gestão de viaturas.

## 🚀 Tecnologias

- Java 17
- Spring Boot 3.4.5
- Maven (estrutura multi-módulo)
- Spring Security + JWT
- JPA + H2/PostgreSQL
- Mail Service (envio de emails de verificação e recuperação de password)
- OpenAPI / Swagger

## 🧱 Estrutura Modular

- `boot` – Ponto de entrada da aplicação
- `configs` – Beans, segurança, validações globais
- `clients` – Integrações externas
- `models` – Entidades e DTOs
- `repositories` – Interfaces JPA
- `services` – Lógica de negócio
- `web` – Controladores REST
- `mappers` – MapStruct e transformações

## 🔧 Como correr localmente

```bash
git clone https://github.com/pneves91/CarDealer.git
cd CarDealer
mvn clean install
```

Para correr o perfil de desenvolvimento com H2:

```bash
cd boot
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## ⚙️ Configuração

Editar o ficheiro `application-dev.yml` com:

```yaml
app:
  frontend-url: http://localhost:3000
  jwt:
    secret: CHANGEME
    expiration: 3600000
    refresh-expiration: 86400000
  mail:
    from: support@cardealer.com
    base-url: http://localhost:8080
```

## 🔐 Funcionalidades da API (AuthController)

- `POST /auth/register` – Registo de utilizador
- `POST /auth/login` – Login e geração de tokens
- `GET /auth/verify-email` – Verificação de email
- `POST /auth/resend-verification-email` – Reenvio de token
- `POST /auth/forgot-password` – Pedido de recuperação
- `POST /auth/reset-password` – Redefinir password
- `POST /auth/logout` – Logout e revogação de token
- `POST /auth/refresh-token` – Gerar novo access token
- `GET /auth/me` – Obter utilizador autenticado

## 🧪 Testes

- Testes automatizados em `AuthServiceTest`
- Testes manuais via Postman com ambiente e scripts pré-configurados
- Swagger disponível em: `http://localhost:8080/swagger-ui.html`

## 👨‍💻 Autor

Pedro Neves  
Consultor de desenvolvimento backend – Java & Spring Boot  
[pedroneves.pt](https://www.pedroneves.pt/)
