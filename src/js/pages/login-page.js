import { AuthService } from "../services/auth-service.js";
import { saveSession } from "../services/session-service.js";
import { setInputError, setText, toggleButtonLoading } from "../utils/dom.js";
import { validateRequired } from "../utils/validators.js";

const AUTH_ERROR_MAP = {
  AUTH_INVALID_CREDENTIALS: "用户名或密码错误",
  AUTH_NOT_ACTIVATED: "请先激活您的账户，检查注册邮箱",
  AUTH_LOCKED: "账户已锁定，请联系管理员",
  AUTH_FORBIDDEN: "当前账户无权限访问系统"
};

function resolveRoute() {
  return "/dashboard.html#overview";
}

function translateAuthError(response) {
  if (!response?.code) {
    return "登录失败，请稍后重试";
  }
  return AUTH_ERROR_MAP[response.code] || response.message || "登录失败，请稍后重试";
}

function clearErrors(elements) {
  setInputError(elements.identityInput, elements.identityError, "");
  setInputError(elements.passwordInput, elements.passwordError, "");
  setText(elements.globalError, "");
}

function validateForm(elements) {
  let valid = true;
  const identity = elements.identityInput.value.trim();
  const password = elements.passwordInput.value;

  if (!validateRequired(identity)) {
    setInputError(elements.identityInput, elements.identityError, "请输入用户名或邮箱");
    valid = false;
  } else {
    setInputError(elements.identityInput, elements.identityError, "");
  }

  if (!validateRequired(password)) {
    setInputError(elements.passwordInput, elements.passwordError, "请输入密码");
    valid = false;
  } else {
    setInputError(elements.passwordInput, elements.passwordError, "");
  }
  return valid;
}

export function initLoginPage() {
  const elements = {
    form: document.querySelector("#loginForm"),
    identityInput: document.querySelector("#loginIdentity"),
    passwordInput: document.querySelector("#loginPassword"),
    identityError: document.querySelector("#loginIdentityError"),
    passwordError: document.querySelector("#loginPasswordError"),
    globalError: document.querySelector("#loginGlobalError"),
    submitButton: document.querySelector("#loginSubmit"),
    togglePasswordButton: document.querySelector("#toggleLoginPassword"),
    ssoButton: document.querySelector("#ssoLoginBtn"),
    languageButton: document.querySelector("#languageToggle"),
    languageText: document.querySelector("#languageText")
  };

  if (!elements.form) return;

  const languages = ["简体中文", "English"];
  let currentLangIndex = 0;

  elements.languageButton?.addEventListener("click", () => {
    currentLangIndex = (currentLangIndex + 1) % languages.length;
    elements.languageText.textContent = languages[currentLangIndex];
  });

  elements.togglePasswordButton?.addEventListener("click", () => {
    const hidden = elements.passwordInput.type === "password";
    elements.passwordInput.type = hidden ? "text" : "password";
  });

  [elements.identityInput, elements.passwordInput].forEach((input) => {
    input?.addEventListener("input", () => {
      clearErrors(elements);
      elements.submitButton.disabled = false;
    });
  });

  elements.ssoButton?.addEventListener("click", async () => {
    toggleButtonLoading(elements.ssoButton, true, "跳转中...");
    const result = await AuthService.startSso();
    toggleButtonLoading(elements.ssoButton, false);

    if (!result.ok) {
      setText(elements.globalError, result.message || "SSO 启动失败");
      return;
    }
    window.location.href = result.data.redirectUrl;
  });

  elements.form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearErrors(elements);
    if (!validateForm(elements)) return;

    toggleButtonLoading(elements.submitButton, true, "登录中...");
    const result = await AuthService.login({
      identity: elements.identityInput.value.trim(),
      password: elements.passwordInput.value
    });
    toggleButtonLoading(elements.submitButton, false);

    if (!result.ok) {
      const message = translateAuthError(result);
      setText(elements.globalError, message);
      if (result.code === "AUTH_LOCKED") {
        elements.submitButton.disabled = true;
        elements.submitButton.textContent = "账户已锁定";
      }
      return;
    }

    saveSession(result.data);
    window.location.href = resolveRoute();
  });
}
