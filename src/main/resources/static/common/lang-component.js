// 通用 - 语言切换+拖拽功能封装（适配PC+移动端，拖拽仅PC生效）
(function() {
    // 1. 状态初始化（从本地存储读取上次选择的语言，默认中文）
    let currentLang = localStorage.getItem('appLang') || 'cn';
    const langSelector = document.getElementById('langSelector');
    const langTrigger = document.getElementById('langTrigger');
    const langCn = document.getElementById('langCn');
    const langEn = document.getElementById('langEn');
    // 判断是否为移动端（通过屏幕宽度+触摸事件支持）
    const isMobile = window.innerWidth <= 576 || 'ontouchstart' in window;

    // 2. 初始化页面语言（页面加载时自动应用上次选择的语言）
    function initLang() {
        // 切换文本显示
        const cnTexts = document.querySelectorAll('.cn');
        const enTexts = document.querySelectorAll('.en');
        if (currentLang === 'cn') {
            cnTexts.forEach(text => text.style.display = 'inline');
            enTexts.forEach(text => text.style.display = 'none');
        } else {
            cnTexts.forEach(text => text.style.display = 'none');
            enTexts.forEach(text => text.style.display = 'inline');
        }

        // 切换输入框占位符（适配有 data-placeholder-en 属性的输入框）
        document.querySelectorAll('input[data-placeholder-en]').forEach(input => {
            input.placeholder = currentLang === 'cn'
                ? input.getAttribute('placeholder')
                : input.getAttribute('data-placeholder-en');
        });

        // 切换选项激活状态（仅PC端语言选择器需要）
        if (langCn && langEn) {
            langCn.classList.toggle('active', currentLang === 'cn');
            langEn.classList.toggle('active', currentLang === 'en');
        }

        // 触发全局语言切换事件，让页面其他组件响应（如评论区、AI按钮）
        window.dispatchEvent(new Event('languageChange'));
    }

    // 3. 语言切换函数（外部可调用，兼容PC下拉弹窗和移动端下拉菜单）
    window.switchLang = function(lang) {
        currentLang = lang;
        // 保存到本地存储，页面切换时持久化
        localStorage.setItem('appLang', lang);
        // 应用新语言
        initLang();
        // 关闭PC端语言选择器下拉菜单（避免切换后菜单仍展开）
        if (langSelector) langSelector.classList.remove('active');
    };

    // 4. 仅PC端初始化：语言选择器展开/收起（移动端不执行）
    function initPcLangSelector() {
        if (!langSelector || !langTrigger || isMobile) return;

        // 展开/收起下拉菜单
        langTrigger.addEventListener('click', () => {
            langSelector.classList.toggle('active');
        });

        // 点击外部关闭菜单
        document.addEventListener('click', (e) => {
            if (langSelector && !langSelector.contains(e.target)) {
                langSelector.classList.remove('active');
            }
        });
    }

    // 5. 仅PC端初始化：长按拖拽功能（移动端不执行，避免触摸冲突）
    function initDragFeature() {
        if (!langSelector || isMobile) return; // 移动端直接跳过拖拽初始化

        let isDragging = false;
        let pressStartTime;
        let startX, startY;
        let offsetX, offsetY;

        // 鼠标按下：记录初始状态
        langSelector.addEventListener('mousedown', function(e) {
            if (e.target.closest('.lang-option')) return; // 点击选项不触发拖拽
            pressStartTime = Date.now();
            const rect = langSelector.getBoundingClientRect();
            startX = e.clientX;
            startY = e.clientY;
            offsetX = startX - rect.left;
            offsetY = startY - rect.top;
        });

        // 鼠标移动：判断长按并更新位置
        document.addEventListener('mousemove', function(e) {
            if (!pressStartTime) return;
            // 长按判断（250ms）+ 未进入拖拽状态
            if (!isDragging) {
                const pressDuration = Date.now() - pressStartTime;
                if (pressDuration > 250) {
                    isDragging = true;
                    langSelector.classList.add('dragging');
                    langSelector.classList.remove('active'); // 拖拽时关闭菜单
                } else {
                    return; // 短按移动不触发拖拽
                }
            }
            // 更新拖拽位置
            updateDragPosition(e);
        });

        // 更新拖拽位置（边界限制）
        function updateDragPosition(e) {
            const windowWidth = window.innerWidth;
            const windowHeight = window.innerHeight;
            const compWidth = langSelector.offsetWidth;
            const compHeight = langSelector.offsetHeight;

            let newLeft = e.clientX - offsetX;
            let newTop = e.clientY - offsetY;

            // 边界限制（留10px边距）
            newLeft = Math.max(10, Math.min(windowWidth - compWidth - 10, newLeft));
            newTop = Math.max(10, Math.min(windowHeight - compHeight - 10, newTop));

            // 应用位置
            langSelector.style.left = `${newLeft}px`;
            langSelector.style.top = `${newTop}px`;
            langSelector.style.right = 'auto';
            langSelector.style.bottom = 'auto';
        }

        // 鼠标松开/离开：重置状态
        function resetDrag() {
            pressStartTime = null;
            isDragging = false;
            langSelector.classList.remove('dragging');
        }
        document.addEventListener('mouseup', resetDrag);
        document.addEventListener('mouseleave', resetDrag);
    }

    // 6. 初始化入口（区分PC/移动端）
    function init() {
        initLang(); // 无论PC/移动端，都初始化语言
        initPcLangSelector(); // 仅PC端初始化下拉菜单交互
        initDragFeature(); // 仅PC端初始化拖拽功能
    }

    // 页面加载时执行初始化
    window.addEventListener('load', init);
})();