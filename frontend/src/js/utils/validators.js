const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,}$/;
const USERNAME_REGEX = /^[a-zA-Z0-9_]{3,20}$/;

export function validateRequired(value) {
  return Boolean(String(value || "").trim());
}

export function validateEmail(email) {
  return EMAIL_REGEX.test(String(email || "").trim());
}

export function validateUsername(username) {
  return USERNAME_REGEX.test(String(username || "").trim());
}

export function validatePassword(password) {
  return PASSWORD_REGEX.test(String(password || ""));
}

export function validatePasswordMatch(password, confirmPassword) {
  return String(password || "") === String(confirmPassword || "");
}
