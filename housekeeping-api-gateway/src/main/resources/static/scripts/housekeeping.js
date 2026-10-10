const state = {
  customers: [],
  services: [],
  workers: [],
  skills: [],
  bookings: [],
  bookingFilter: ''
};

const api = {
  customers: '/api/customer/customers',
  services: '/api/vet/service-items',
  workers: '/api/vet/housekeepers',
  skills: '/api/vet/service-skills',
  bookings: '/api/visit/bookings'
};

const statusText = {
  PENDING_CONFIRMATION: '待确认',
  PENDING_ASSIGNMENT: '待分配',
  ASSIGNED: '已分配',
  ACCEPTED: '已接单',
  IN_SERVICE: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
};

const pricingText = {
  PER_HOUR: '小时',
  PER_AREA: '平方米',
  PER_ITEM: '件',
  FIXED_PRICE: '次'
};

let authenticatedInitializationComplete = false;
window.addEventListener('housekeeping:authenticated', initializeAuthenticatedApplication);

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.nav-links button').forEach(button => {
    button.onclick = () => showView(button.dataset.view);
  });

  document.querySelectorAll('.filters button').forEach(button => {
    button.onclick = () => {
      state.bookingFilter = button.dataset.status;
      document.querySelectorAll('.filters button').forEach(item => item.classList.remove('active'));
      button.classList.add('active');
      renderBookings();
    };
  });

  el('bookingCustomer').onchange = () => { loadAddresses(); findWorkers(true); };
  el('bookingAddress').onchange = () => findWorkers(true);
  el('bookingService').onchange = () => { updateQuote(); findWorkers(true); };
  el('bookingDate').onchange = () => findWorkers(true);
  el('bookingStart').onchange = () => findWorkers(true);
  el('bookingQuantity').oninput = updateQuote;
  el('bookingForm').onsubmit = createBooking;
  el('customerForm').onsubmit = createCustomer;
  el('workerForm').onsubmit = createWorker;
  el('refreshWorkers').addEventListener('click', () => findWorkers());
  document.addEventListener('click', event => {
    const target = event.target.closest('[data-view-target],[data-open-booking],[data-dialog-open],[data-dialog-close],[data-book-service],[data-booking-action]');
    if (!target) return;
    if (target.dataset.viewTarget) showView(target.dataset.viewTarget);
    else if (target.hasAttribute('data-open-booking')) openBooking();
    else if (target.dataset.dialogOpen) openDialog(target.dataset.dialogOpen);
    else if (target.dataset.dialogClose) closeDialog(target.dataset.dialogClose);
    else if (target.dataset.bookService) bookService(Number(target.dataset.bookService));
    else if (target.dataset.bookingAction) changeBooking(Number(target.dataset.bookingId), target.dataset.bookingAction);
  });

  const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  el('bookingDate').value = tomorrow;
  el('currentDate').textContent = new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short'
  }).format(new Date());

  if (HousekeepingAuth.getSession() && !authenticatedInitializationComplete) initializeAuthenticatedApplication();
});

function initializeAuthenticatedApplication() {
  authenticatedInitializationComplete = true;
  const session = HousekeepingAuth.getSession();
  showView(session?.role === 'WORKER' ? 'bookings' : 'dashboard');
  loadAll();
}

async function request(url, options) {
  const response = await HousekeepingAuth.fetch(url, options || {});

  if (!response.ok) {
    throw new Error(await response.text() || `HTTP ${response.status}`);
  }

  return response.status === 204 ? null : response.json();
}

async function loadAll() {
  try {
    const session = HousekeepingAuth.getSession();
    if (!session) return;
    const customerRequest = session.role === 'ADMIN'
      ? request(api.customers)
      : session.role === 'CUSTOMER'
        ? request(`${api.customers}/${session.userId}`).then(customer => [customer])
        : Promise.resolve([]);
    const bookingUrl = session.role === 'CUSTOMER'
      ? `${api.bookings}?customerId=${encodeURIComponent(session.userId)}`
      : session.role === 'WORKER'
        ? `${api.bookings}?housekeeperId=${encodeURIComponent(session.userId)}`
        : api.bookings;
    [
      state.customers,
      state.services,
      state.workers,
      state.skills,
      state.bookings
    ] = await Promise.all([
      customerRequest,
      request(api.services),
      request(api.workers),
      request(api.skills),
      request(bookingUrl)
    ]);
    renderAll();
  } catch (error) {
    toast(`部分服务尚未启动：${error.message}`);
  }
}

function renderAll() {
  renderStats();
  renderServices();
  renderWorkers();
  renderCustomers();
  renderBookings();
  fillSelects();
}

function renderStats() {
  setText('customerCount', state.customers.length);
  setText('bookingCount', state.bookings.length);
  setText('workerCount', state.workers.filter(worker => worker.status === 'AVAILABLE').length);
  setText('serviceCount', state.services.length);
  setText('pendingCount', state.bookings.filter(booking => !['COMPLETED', 'CANCELLED'].includes(booking.status)).length);
  setText('completedCount', state.bookings.filter(booking => booking.status === 'COMPLETED').length);

  const today = new Date().toISOString().slice(0, 10);
  setText('todayCount', state.bookings.filter(booking =>
    booking.serviceDate === today && !['COMPLETED', 'CANCELLED'].includes(booking.status)
  ).length);
}

function serviceCard(service, index, includeButton) {
  const number = String(index + 1).padStart(2, '0');
  return `
    <article class="service-card">
      <span class="service-index">${number} / SERVICE</span>
      <h3>${esc(service.name)}</h3>
      <p>${esc(service.description || '标准化上门家政服务')}</p>
      <div class="service-bottom">
        <span class="price">¥${money(service.unitPrice)} / ${pricingText[service.pricingType] || '次'}</span>
        ${includeButton ? `<button class="action" data-book-service="${Number(service.id)}">立即预约</button>` : ''}
      </div>
    </article>`;
}

function renderServices() {
  const canBook = HousekeepingAuth.hasRole('ADMIN', 'CUSTOMER');
  el('serviceGrid').innerHTML = state.services
    .map((service, index) => serviceCard(service, index, canBook))
    .join('');

  el('featuredServices').innerHTML = state.services
    .slice(0, 4)
    .map((service, index) => serviceCard(service, index, false))
    .join('');
}

function renderWorkers() {
  el('workerGrid').innerHTML = state.workers.map(worker => `
    <article class="worker-card">
      <div class="worker-head">
        <div class="avatar">${esc(worker.name.slice(0, 1))}</div>
        <div>
          <h3>${esc(worker.name)}</h3>
          <span class="rating">评分 ${worker.rating || '5.0'}</span>
        </div>
        <span class="status">${worker.status === 'AVAILABLE' ? '可接单' : '忙碌'}</span>
      </div>
      <p>${esc(worker.introduction || '专业家政服务人员')}</p>
      <small>${worker.experienceYears || 0} 年经验 · ${esc(worker.serviceDistrict || '全市')}</small>
      <div class="tags">
        ${(worker.skills || []).map(skill => `<span class="tag">${esc(skill.name)}</span>`).join('')}
      </div>
    </article>
  `).join('');
}

function renderCustomers() {
  el('customerRows').innerHTML = state.customers.map(customer => `
    <tr>
      <td><b>${esc(customer.name)}</b><small>客户编号 #${customer.id}</small></td>
      <td>${esc(customer.telephone)}</td>
      <td>${esc(customer.email || '-')}</td>
      <td>
        ${(customer.addresses || []).map(address => `
          <div>
            ${esc(address.city + address.district + address.detailAddress)}
            ${address.defaultAddress ? ' <span class="tag">默认</span>' : ''}
          </div>
        `).join('') || '暂无地址'}
      </td>
    </tr>
  `).join('');
}

function renderBookings() {
  const rows = state.bookings
    .filter(booking => !state.bookingFilter || booking.status === state.bookingFilter)
    .map(booking => {
      const customer = state.customers.find(item => item.id === booking.customerId);
      const service = state.services.find(item => item.id === booking.serviceItemId);
      const worker = state.workers.find(item => item.id === booking.housekeeperId);

      return `
        <tr>
          <td><b>${esc(booking.bookingNumber)}</b><small>#${booking.id}</small></td>
          <td>${esc(customer?.name || `客户 ${booking.customerId}`)}<small>${esc(service?.name || `服务 ${booking.serviceItemId}`)}</small></td>
          <td>${booking.serviceDate}<small>${shortTime(booking.startTime)} — ${shortTime(booking.endTime)}</small></td>
          <td>${esc(worker?.name || '待分配')}</td>
          <td>¥${money(booking.totalPrice)}</td>
          <td><span class="status">${statusText[booking.status] || booking.status}</span></td>
          <td>${bookingActions(booking)}</td>
        </tr>`;
    })
    .join('');

  el('bookingRows').innerHTML = rows || '<tr><td colspan="7">暂无符合条件的订单</td></tr>';
}

function bookingActions(booking) {
  const role = HousekeepingAuth.getSession()?.role;
  if (role === 'CUSTOMER') {
    return ['PENDING_ASSIGNMENT', 'ASSIGNED', 'ACCEPTED'].includes(booking.status)
      ? actionButton(booking.id, 'cancel', '取消', 'action danger')
      : '—';
  }
  if (role === 'WORKER') {
    if (booking.status === 'ASSIGNED')
      return actionButton(booking.id, 'accept', '接单');
    if (booking.status === 'ACCEPTED')
      return actionButton(booking.id, 'start', '开始服务');
    if (booking.status === 'IN_SERVICE')
      return actionButton(booking.id, 'complete', '完成服务');
    return '—';
  }
  if (role === 'ADMIN' && booking.status === 'PENDING_ASSIGNMENT') {
    return '待分配服务人员';
  }
  if (booking.status === 'ASSIGNED') {
    return actionButton(booking.id, 'accept', '接单')
      + actionButton(booking.id, 'cancel', '取消', 'action danger');
  }
  if (booking.status === 'ACCEPTED') {
    return actionButton(booking.id, 'start', '开始服务')
      + actionButton(booking.id, 'cancel', '取消', 'action danger');
  }
  if (booking.status === 'IN_SERVICE') {
    return actionButton(booking.id, 'complete', '完成服务');
  }
  return '—';
}

function actionButton(id, action, label, className = 'action') {
  return `<button class="${className}" data-booking-id="${Number(id)}" data-booking-action="${action}">${label}</button>`;
}

function fillSelects() {
  el('bookingCustomer').innerHTML = state.customers
    .map(customer => `<option value="${customer.id}">${esc(customer.name)} · ${esc(customer.telephone)}</option>`)
    .join('');

  el('bookingService').innerHTML = state.services
    .map(service => `<option value="${service.id}">${esc(service.name)}</option>`)
    .join('');

  el('workerSkills').innerHTML = state.skills
    .map(skill => `<option value="${skill.id}">${esc(skill.name)}</option>`)
    .join('');

  const availableWorkers = state.workers.filter(worker => worker.status === 'AVAILABLE');
  el('bookingWorker').innerHTML = '<option value="">请选择服务人员</option>' + availableWorkers
    .map(worker => `<option value="${worker.id}">${esc(worker.name)} · 评分 ${worker.rating || '5.0'} · ${esc(worker.serviceDistrict)}</option>`)
    .join('');

  loadAddresses();
  updateQuote();
}

function loadAddresses() {
  const customer = state.customers.find(item => item.id === Number(el('bookingCustomer').value));
  el('bookingAddress').innerHTML = (customer?.addresses || [])
    .map(address => `
      <option value="${address.id}" data-district="${esc(address.district)}">
        ${esc(address.district + address.detailAddress)}
      </option>`)
    .join('');
}

function updateQuote() {
  const service = state.services.find(item => item.id === Number(el('bookingService').value));
  if (!service) return;

  const quantity = Math.max(
    Number(el('bookingQuantity').value) || 0,
    Number(service.minimumUnits) || 0
  );

  setText('bookingPrice', `¥${money(Number(service.unitPrice) * quantity)}`);
  setText(
    'pricingHint',
    `¥${money(service.unitPrice)} / ${pricingText[service.pricingType] || '次'}，最低 ${service.minimumUnits}`
  );
}

async function findWorkers(silent = false) {
  const serviceItemId = el('bookingService').value;
  const date = el('bookingDate').value;
  const startTime = el('bookingStart').value;
  const address = el('bookingAddress').selectedOptions[0];
  const district = address?.dataset.district || '';

  if (!serviceItemId || !date || !startTime) {
    if (!silent) toast('请先填写服务项目和时间');
    return;
  }

  try {
    const workers = await request(
      `/api/vet/housekeepers/available?serviceItemId=${serviceItemId}&date=${date}&startTime=${startTime}&district=${encodeURIComponent(district)}`
    );

    el('bookingWorker').innerHTML = workers.length
      ? workers.map(worker =>
          `<option value="${worker.id}">${esc(worker.name)} · 评分 ${worker.rating} · ${esc(worker.serviceDistrict)}</option>`
        ).join('')
      : '<option value="">当前没有可用人员</option>';

    if (!silent) toast(workers.length ? `已找到 ${workers.length} 位可用人员` : '当前时间没有可用人员');
  } catch (error) {
    if (!silent) toast(`查询失败：${error.message}`);
  }
}

async function createBooking(event) {
  event.preventDefault();
  if (!HousekeepingAuth.hasRole('ADMIN', 'CUSTOMER')) {
    toast('当前角色不能创建预约');
    return;
  }
  const service = state.services.find(item => item.id === Number(el('bookingService').value));
  const quantity = Math.max(Number(el('bookingQuantity').value), Number(service.minimumUnits));

  const payload = {
    customerId: Number(el('bookingCustomer').value),
    addressId: Number(el('bookingAddress').value),
    serviceItemId: service.id,
    housekeeperId: Number(el('bookingWorker').value),
    serviceDate: el('bookingDate').value,
    startTime: el('bookingStart').value,
    endTime: el('bookingEnd').value,
    quantity,
    unitPrice: service.unitPrice,
    customerRemark: el('bookingRemark').value
  };

  if (!payload.housekeeperId) {
    toast('请先查询并选择可用服务人员');
    return;
  }

  try {
    await request(api.bookings, { method: 'POST', body: JSON.stringify(payload) });
    closeDialog('bookingDialog');
    const session = HousekeepingAuth.getSession();
    state.bookings = await request(session.role === 'CUSTOMER'
      ? `${api.bookings}?customerId=${encodeURIComponent(session.userId)}` : api.bookings);
    renderStats();
    renderBookings();
    toast('预约创建成功');
  } catch (error) {
    toast(`创建失败：${error.message}`);
  }
}

async function createCustomer(event) {
  event.preventDefault();
  if (!HousekeepingAuth.hasRole('ADMIN')) return toast('只有管理员可以新增客户');
  try {
    const customer = await request(api.customers, {
      method: 'POST',
      body: JSON.stringify({
        name: el('customerName').value,
        telephone: el('customerTelephone').value,
        email: el('customerEmail').value
      })
    });

    await request(`${api.customers}/${customer.id}/addresses`, {
      method: 'POST',
      body: JSON.stringify({
        contactName: customer.name,
        telephone: customer.telephone,
        city: el('addressCity').value,
        district: el('addressDistrict').value,
        detailAddress: el('addressDetail').value,
        accessInstruction: el('addressInstruction').value,
        defaultAddress: true
      })
    });

    closeDialog('customerDialog');
    state.customers = await request(api.customers);
    renderAll();
    event.target.reset();
    toast('客户创建成功');
  } catch (error) {
    toast(`保存失败：${error.message}`);
  }
}

async function createWorker(event) {
  event.preventDefault();
  if (!HousekeepingAuth.hasRole('ADMIN')) return toast('只有管理员可以新增服务人员');
  const skillIds = [...el('workerSkills').selectedOptions].map(option => Number(option.value));

  try {
    await request(api.workers, {
      method: 'POST',
      body: JSON.stringify({
        name: el('workerName').value,
        telephone: el('workerTelephone').value,
        introduction: el('workerIntroduction').value,
        experienceYears: Number(el('workerExperience').value),
        serviceDistrict: el('workerDistrict').value,
        skillIds
      })
    });

    closeDialog('workerDialog');
    state.workers = await request(api.workers);
    renderAll();
    event.target.reset();
    toast('服务人员创建成功');
  } catch (error) {
    toast(`保存失败：${error.message}`);
  }
}

async function changeBooking(id, action) {
  if (action === 'cancel' && !confirm('确认取消这个预约吗？')) return;

  try {
    await request(`${api.bookings}/${id}/${action}`, { method: 'POST', body: '{}' });
    const session = HousekeepingAuth.getSession();
    const bookingUrl = session.role === 'CUSTOMER'
      ? `${api.bookings}?customerId=${encodeURIComponent(session.userId)}`
      : session.role === 'WORKER'
        ? `${api.bookings}?housekeeperId=${encodeURIComponent(session.userId)}`
        : api.bookings;
    state.bookings = await request(bookingUrl);
    renderStats();
    renderBookings();
    toast('订单状态已更新');
  } catch (error) {
    toast(`操作失败：${error.message}`);
  }
}

function showView(id) {
  document.querySelectorAll('.view').forEach(view => view.classList.toggle('active', view.id === id));
  document.querySelectorAll('.nav-links button').forEach(button =>
    button.classList.toggle('active', button.dataset.view === id)
  );
  window.scrollTo(0, 0);
}

function openBooking() {
  if (!HousekeepingAuth.hasRole('ADMIN', 'CUSTOMER')) {
    toast('服务人员账号不能创建预约');
    return;
  }
  openDialog('bookingDialog');
  updateQuote();
  findWorkers(true);
}

function bookService(id) {
  if (!HousekeepingAuth.hasRole('ADMIN', 'CUSTOMER')) return;
  openBooking();
  el('bookingService').value = id;
  updateQuote();
  findWorkers(true);
}

function openDialog(id) { el(id).showModal(); }
function closeDialog(id) { el(id).close(); }

function toast(message) {
  const element = el('toast');
  element.textContent = message;
  element.classList.add('show');
  clearTimeout(element.timer);
  element.timer = setTimeout(() => element.classList.remove('show'), 3200);
}

function setText(id, text) { el(id).textContent = text; }
function el(id) { return document.getElementById(id); }
function money(value) { return Number(value || 0).toFixed(2); }
function shortTime(value) { return (value || '').slice(0, 5); }

function esc(value) {
  const element = document.createElement('div');
  element.textContent = value ?? '';
  return element.innerHTML;
}
