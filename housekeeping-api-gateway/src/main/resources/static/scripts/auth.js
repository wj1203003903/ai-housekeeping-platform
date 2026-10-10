(function () {
  const STORAGE_KEY = 'housekeepingAuth';
  const roleNames = { CUSTOMER: '客户', WORKER: '服务人员', ADMIN: '管理员' };
  let session = readSession();

  function readSession() {
    try {
      const value = JSON.parse(sessionStorage.getItem(STORAGE_KEY));
      if (!value?.accessToken || !value?.expiresAt || new Date(value.expiresAt) <= new Date()) {
        sessionStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return value;
    } catch (_) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }

  async function login(username, password) {
    const response = await fetch('/api/genai/auth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    if (!response.ok) throw new Error(response.status === 401 ? '账号或密码错误' : '登录服务暂时不可用');
    session = await response.json();
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    applySession();
    window.dispatchEvent(new CustomEvent('housekeeping:authenticated'));
    return session;
  }

  async function readError(response, fallback) {
    try {
      const body = await response.json();
      return body.message || fallback;
    } catch (_) {
      return fallback;
    }
  }

  function setRegistrationMode(role) {
    const customer = role === 'CUSTOMER';
    document.getElementById('registrationRole').value = role;
    document.getElementById('registrationTitle').textContent = customer ? '注册客户账号' : '注册服务人员';
    document.getElementById('registrationEyebrow').textContent = customer ? 'CREATE CLIENT ACCOUNT' : 'JOIN AS PROFESSIONAL';
    document.getElementById('registrationLead').textContent = customer
      ? '填写账号、联系方式和默认服务地址，注册后可直接预约。'
      : '创建服务人员账号并选择可提供的服务技能，注册后进入接单工作台。';
    document.getElementById('customerRegistrationFields').hidden = !customer;
    document.getElementById('workerRegistrationFields').hidden = customer;
    ['registrationCity', 'registrationDistrict', 'registrationAddress'].forEach(id => {
      document.getElementById(id).required = customer;
    });
    ['registrationExperience', 'registrationServiceDistrict', 'registrationSkills'].forEach(id => {
      document.getElementById(id).required = !customer;
    });
  }

  async function openRegistration(role) {
    const dialog = document.getElementById('registrationDialog');
    const form = document.getElementById('registrationForm');
    form.reset();
    document.getElementById('registrationError').textContent = '';
    setRegistrationMode(role);
    if (role === 'WORKER') {
      try {
        const response = await fetch('/api/genai/auth/registration-options');
        if (!response.ok) throw new Error('暂时无法读取服务技能');
        const skills = await response.json();
        const select = document.getElementById('registrationSkills');
        select.replaceChildren(...skills.map(skill => {
          const option = document.createElement('option');
          option.value = String(Number(skill.id));
          option.textContent = String(skill.name || '');
          return option;
        }));
      } catch (error) {
        document.getElementById('registrationError').textContent = error.message;
      }
    }
    dialog.showModal();
  }

  async function register(event) {
    event.preventDefault();
    const role = document.getElementById('registrationRole').value;
    const submit = document.getElementById('registrationSubmit');
    const error = document.getElementById('registrationError');
    const username = document.getElementById('registrationUsername').value.trim();
    const password = document.getElementById('registrationPassword').value;
    const common = {
      username,
      password,
      name: document.getElementById('registrationName').value.trim(),
      telephone: document.getElementById('registrationTelephone').value.trim()
    };
    const payload = role === 'CUSTOMER' ? {
      ...common,
      email: document.getElementById('registrationEmail').value.trim(),
      city: document.getElementById('registrationCity').value.trim(),
      district: document.getElementById('registrationDistrict').value.trim(),
      detailAddress: document.getElementById('registrationAddress').value.trim(),
      accessInstruction: document.getElementById('registrationAccess').value.trim()
    } : {
      ...common,
      experienceYears: Number(document.getElementById('registrationExperience').value),
      serviceDistrict: document.getElementById('registrationServiceDistrict').value.trim(),
      introduction: document.getElementById('registrationIntroduction').value.trim(),
      skillIds: [...document.getElementById('registrationSkills').selectedOptions]
        .map(option => Number(option.value))
    };
    submit.disabled = true;
    submit.textContent = '正在创建账号…';
    error.textContent = '';
    try {
      const response = await fetch(`/api/genai/auth/register/${role === 'CUSTOMER' ? 'customer' : 'worker'}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!response.ok) throw new Error(await readError(response,
        response.status === 400 ? '请检查注册信息是否完整' : '注册服务暂时不可用'));
      document.getElementById('registrationDialog').close();
      await login(username, password);
    } catch (exception) {
      error.textContent = exception.message;
    } finally {
      submit.disabled = false;
      submit.textContent = '注册并进入系统';
    }
  }

  async function logout() {
    try {
      await fetch('/api/genai/auth/logout', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}'
      });
    } catch (_) { /* Local logout must still complete. */ }
    session = null;
    sessionStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem('housekeepingAccessToken');
    localStorage.removeItem('housekeepingConversationId');
    applySession();
  }

  async function authenticatedFetch(url, options = {}) {
    if (!session) throw new Error('请先登录');
    const headers = new Headers(options.headers || {});
    if (!headers.has('Content-Type') && options.body) headers.set('Content-Type', 'application/json');
    headers.set('Authorization', `Bearer ${session.accessToken}`);
    let response = await fetch(url, { ...options, headers });
    if (response.status === 401) {
      const refreshed = await fetch('/api/genai/auth/refresh', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}'
      });
      if (refreshed.ok) {
        session = await refreshed.json();
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        headers.set('Authorization', `Bearer ${session.accessToken}`);
        response = await fetch(url, { ...options, headers });
      } else {
        await logout();
        throw new Error('登录已过期，请重新登录');
      }
    }
    if (response.status === 403) throw new Error('当前账号没有执行该操作的权限');
    return response;
  }

  function setVisible(selector, visible) {
    document.querySelectorAll(selector).forEach(element => { element.hidden = !visible; });
  }

  function applySession() {
    const loginScreen = document.getElementById('loginScreen');
    const app = document.getElementById('applicationShell');
    if (!loginScreen || !app) return;
    loginScreen.classList.toggle('hidden', Boolean(session));
    app.classList.toggle('auth-pending', !session);
    if (!session) return;

    document.getElementById('accountName').textContent = session.userId;
    document.getElementById('accountRole').textContent = roleNames[session.role] || session.role;
    document.getElementById('accountAvatar').textContent = (roleNames[session.role] || '用').slice(0, 1);
    setVisible('[data-view="customers"]', session.role === 'ADMIN');
    setVisible('[data-dialog-open="customerDialog"]', session.role === 'ADMIN');
    setVisible('[data-dialog-open="workerDialog"]', session.role === 'ADMIN');
    setVisible('[data-open-booking]', session.role !== 'WORKER');
  }

  function initialize() {
    const form = document.getElementById('loginForm');
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const submit = document.getElementById('loginSubmit');
      const error = document.getElementById('loginError');
      submit.disabled = true;
      submit.textContent = '正在登录…';
      error.textContent = '';
      try {
        await login(document.getElementById('loginUsername').value.trim(),
          document.getElementById('loginPassword').value);
        form.reset();
      } catch (exception) {
        error.textContent = exception.message;
      } finally {
        submit.disabled = false;
        submit.textContent = '登录';
      }
    });
    document.getElementById('openCustomerRegister').addEventListener('click', () => openRegistration('CUSTOMER'));
    document.getElementById('openWorkerRegister').addEventListener('click', () => openRegistration('WORKER'));
    document.getElementById('registrationForm').addEventListener('submit', register);
    ['closeRegistration', 'cancelRegistration'].forEach(id => {
      document.getElementById(id).addEventListener('click', () => document.getElementById('registrationDialog').close());
    });
    document.getElementById('logoutButton').addEventListener('click', logout);
    applySession();
    if (session) window.dispatchEvent(new CustomEvent('housekeeping:authenticated'));
  }

  window.HousekeepingAuth = {
    initialize,
    fetch: authenticatedFetch,
    logout,
    getSession: () => session,
    getAccessToken: () => session?.accessToken || null,
    hasRole: (...roles) => Boolean(session && roles.includes(session.role))
  };

  document.addEventListener('DOMContentLoaded', initialize);
})();
