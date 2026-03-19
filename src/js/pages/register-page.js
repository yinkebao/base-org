import { AuthService } from "../services/auth-service.js";
import { saveSession } from "../services/session-service.js";
import { debounce, setInputError, setText, toggleButtonLoading } from "../utils/dom.js";
import { getPasswordStrength } from "../utils/password-strength.js";
import {
  validateEmail,
  validatePassword,
  validatePasswordMatch,
  validateUsername
} from "../utils/validators.js";

function mapRegisterError(code, fallback = "注册失败，请稍后重试") {
  const messageMap = {
    USERNAME_TAKEN: "用户名已存在，请更换",
    EMAIL_TAKEN: "邮箱已被注册",
    PASSWORD_WEAK: "密码复杂度不足，请包含大小写字母、数字和特殊字符",
    NETWORK_ERROR: "网络异常，请检查连接后重试"
  };
  return messageMap[code] || fallback;
}

export function initRegisterPage() {
  const elements = {
    form: document.querySelector("#registerForm"),
    usernameInput: document.querySelector("#registerUsername"),
    emailInput: document.querySelector("#registerEmail"),
    passwordInput: document.querySelector("#registerPassword"),
    confirmPasswordInput: document.querySelector("#registerConfirmPassword"),
    agreeTermsInput: document.querySelector("#agreeTerms"),
    usernameHint: document.querySelector("#registerUsernameHint"),
    usernameStatusIcon: document.querySelector("#usernameStatusIcon"),
    usernameError: document.querySelector("#registerUsernameError"),
    emailError: document.querySelector("#registerEmailError"),
    passwordError: document.querySelector("#registerPasswordError"),
    confirmPasswordError: document.querySelector("#registerConfirmPasswordError"),
    termsError: document.querySelector("#registerTermsError"),
    globalError: document.querySelector("#registerGlobalError"),
    submitButton: document.querySelector("#registerSubmit"),
    togglePasswordButton: document.querySelector("#toggleRegisterPassword"),
    strengthText: document.querySelector("#passwordStrengthText"),
    strengthBars: document.querySelectorAll(".strength__bars span"),
    languageButton: document.querySelector("#languageToggle"),
    languageText: document.querySelector("#languageText")
  };
  if (!elements.form) return;

  const state = {
    usernameAvailable: false,
    submitted: false,
    touched: {
      username: false,
      email: false,
      password: false,
      confirmPassword: false,
      terms: false
    }
  };

  const languageList = ["简体中文", "English"];
  let languageIndex = 0;

  function setSubmitEnabled(enabled) {
    elements.submitButton.disabled = !enabled;
    elements.submitButton.classList.toggle("btn--disabled", !enabled);
  }

  function updatePasswordStrength() {
    const strength = getPasswordStrength(elements.passwordInput.value);
    elements.strengthText.textContent = `密码强度：${strength.text}`;
    elements.strengthBars.forEach((bar, idx) => {
      bar.classList.toggle("is-active", idx < strength.level);
    });
  }

  function shouldShowError(field) {
    return state.submitted || state.touched[field];
  }

  function validateUsernameField(showError = shouldShowError("username")) {
    const username = elements.usernameInput.value.trim();
    if (!validateUsername(username)) {
      if (showError) {
        setInputError(elements.usernameInput, elements.usernameError, "用户名需为3-20位字母、数字或下划线");
      } else {
        setInputError(elements.usernameInput, elements.usernameError, "");
      }
      if (showError) setText(elements.usernameHint, "");
      state.usernameAvailable = false;
      elements.usernameStatusIcon.className = showError ? "status-icon status-icon--invalid" : "status-icon";
      return false;
    }
    setInputError(elements.usernameInput, elements.usernameError, "");
    return true;
  }

  function validateEmailField(showError = shouldShowError("email")) {
    const valid = validateEmail(elements.emailInput.value.trim());
    setInputError(elements.emailInput, elements.emailError, valid || !showError ? "" : "邮箱格式不正确");
    return valid;
  }

  function validatePasswordField(showError = shouldShowError("password")) {
    const valid = validatePassword(elements.passwordInput.value);
    setInputError(
      elements.passwordInput,
      elements.passwordError,
      valid || !showError ? "" : "至少8位，并包含大小写字母、数字和特殊字符"
    );
    return valid;
  }

  function validateConfirmField(showError = shouldShowError("confirmPassword")) {
    const valid = validatePasswordMatch(elements.passwordInput.value, elements.confirmPasswordInput.value);
    setInputError(elements.confirmPasswordInput, elements.confirmPasswordError, valid || !showError ? "" : "两次输入密码不一致");
    return valid;
  }

  function validateTermsField(showError = shouldShowError("terms")) {
    const valid = elements.agreeTermsInput.checked;
    setText(elements.termsError, valid || !showError ? "" : "请先同意服务条款与隐私政策");
    return valid;
  }

  function refreshSubmitState() {
    const valid =
      validateUsernameField() &&
      validateEmailField() &&
      validatePasswordField() &&
      validateConfirmField() &&
      validateTermsField() &&
      state.usernameAvailable;
    setSubmitEnabled(valid);
  }

  const checkUsernameAvailability = debounce(async () => {
    if (!validateUsernameField()) {
      refreshSubmitState();
      return;
    }
    const result = await AuthService.checkUsername({
      username: elements.usernameInput.value.trim()
    });

    if (!result.ok) {
      state.usernameAvailable = false;
      elements.usernameStatusIcon.className = "status-icon status-icon--invalid";
      setText(elements.usernameHint, mapRegisterError(result.code, result.message));
    } else {
      state.usernameAvailable = true;
      elements.usernameStatusIcon.className = "status-icon status-icon--valid";
      setText(elements.usernameHint, "该用户名可用");
    }
    refreshSubmitState();
  }, 280);

  elements.languageButton?.addEventListener("click", () => {
    languageIndex = (languageIndex + 1) % languageList.length;
    elements.languageText.textContent = languageList[languageIndex];
  });

  elements.togglePasswordButton?.addEventListener("click", () => {
    const hidden = elements.passwordInput.type === "password";
    elements.passwordInput.type = hidden ? "text" : "password";
  });

  elements.usernameInput.addEventListener("input", () => {
    state.touched.username = true;
    setText(elements.globalError, "");
    checkUsernameAvailability();
  });

  elements.usernameInput.addEventListener("blur", () => {
    state.touched.username = true;
    refreshSubmitState();
  });

  [elements.emailInput, elements.passwordInput, elements.confirmPasswordInput].forEach((input) => {
    input.addEventListener("input", () => {
      setText(elements.globalError, "");
      if (input === elements.emailInput) state.touched.email = true;
      if (input === elements.passwordInput) state.touched.password = true;
      if (input === elements.confirmPasswordInput) state.touched.confirmPassword = true;
      updatePasswordStrength();
      refreshSubmitState();
    });
  });

  elements.agreeTermsInput.addEventListener("change", () => {
    state.touched.terms = true;
    refreshSubmitState();
  });

  elements.form.addEventListener("submit", async (event) => {
    event.preventDefault();
    state.submitted = true;
    refreshSubmitState();
    if (elements.submitButton.disabled) return;

    toggleButtonLoading(elements.submitButton, true, "注册中...");
    const result = await AuthService.register({
      username: elements.usernameInput.value.trim(),
      email: elements.emailInput.value.trim(),
      password: elements.passwordInput.value
    });
    toggleButtonLoading(elements.submitButton, false);
    refreshSubmitState();

    if (!result.ok) {
      setText(elements.globalError, mapRegisterError(result.code, result.message));
      return;
    }

    saveSession(result.data);
    window.location.href = "/dashboard.html#overview";
  });

  updatePasswordStrength();
  refreshSubmitState();
}
