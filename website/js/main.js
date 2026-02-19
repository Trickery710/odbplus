/**
 * ODB+ Website Main JavaScript
 * Handles navigation, search, and interactive features
 */

document.addEventListener('DOMContentLoaded', function() {
    initNavbar();
    initSearch();
    initMobileMenu();
    initSmoothScroll();
    initCodeLookup();
    checkAuthState();
});

/**
 * Navbar scroll behavior
 */
function initNavbar() {
    const navbar = document.getElementById('navbar');
    if (!navbar) return;

    let lastScroll = 0;
    const scrollThreshold = 50;

    window.addEventListener('scroll', function() {
        const currentScroll = window.pageYOffset;

        // Add shadow when scrolled
        if (currentScroll > 10) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }

        // Hide/show navbar on scroll (optional - uncomment to enable)
        // if (currentScroll > lastScroll && currentScroll > scrollThreshold) {
        //     navbar.classList.add('hidden');
        // } else {
        //     navbar.classList.remove('hidden');
        // }

        lastScroll = currentScroll;
    });
}

/**
 * Search functionality
 */
function initSearch() {
    // Code search on main page
    const codeSearchInput = document.getElementById('codeSearch');
    if (codeSearchInput) {
        codeSearchInput.addEventListener('input', debounce(handleCodeSearch, 300));
        codeSearchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                handleCodeSearch();
            }
        });
    }

    // Quick code lookup on homepage
    const quickCodeInput = document.getElementById('quickCodeInput');
    const quickCodeBtn = document.getElementById('quickCodeBtn');
    if (quickCodeInput && quickCodeBtn) {
        quickCodeBtn.addEventListener('click', function() {
            lookupCode(quickCodeInput.value);
        });
        quickCodeInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                lookupCode(quickCodeInput.value);
            }
        });
    }

    // Forum search
    const forumSearch = document.getElementById('forumSearch');
    if (forumSearch) {
        forumSearch.addEventListener('input', debounce(handleForumSearch, 300));
    }

    // Model search on make pages
    const modelSearch = document.getElementById('modelSearch');
    if (modelSearch) {
        modelSearch.addEventListener('input', debounce(handleModelSearch, 300));
    }

    // Sensor search
    const sensorSearch = document.getElementById('sensorSearch');
    if (sensorSearch) {
        sensorSearch.addEventListener('input', debounce(handleSensorSearch, 300));
    }
}

/**
 * Handle code search
 */
function handleCodeSearch() {
    const input = document.getElementById('codeSearch');
    if (!input) return;

    const query = input.value.trim().toUpperCase();
    const codeItems = document.querySelectorAll('.code-item, .code-card');

    if (query.length === 0) {
        codeItems.forEach(item => item.style.display = '');
        return;
    }

    codeItems.forEach(item => {
        const text = item.textContent.toUpperCase();
        if (text.includes(query)) {
            item.style.display = '';
        } else {
            item.style.display = 'none';
        }
    });
}

/**
 * Handle forum search
 */
function handleForumSearch() {
    const input = document.getElementById('forumSearch');
    if (!input) return;

    const query = input.value.trim().toLowerCase();
    const forumCards = document.querySelectorAll('.forum-card');

    if (query.length === 0) {
        forumCards.forEach(card => card.style.display = '');
        return;
    }

    forumCards.forEach(card => {
        const text = card.textContent.toLowerCase();
        if (text.includes(query)) {
            card.style.display = '';
        } else {
            card.style.display = 'none';
        }
    });
}

/**
 * Handle model search on make pages
 */
function handleModelSearch() {
    const input = document.getElementById('modelSearch');
    if (!input) return;

    const query = input.value.trim().toLowerCase();
    const modelCards = document.querySelectorAll('.model-card');
    const modelListItems = document.querySelectorAll('.model-list-section li');

    if (query.length === 0) {
        modelCards.forEach(card => card.style.display = '');
        modelListItems.forEach(item => item.style.display = '');
        document.querySelectorAll('.model-list-section').forEach(section => section.style.display = '');
        return;
    }

    // Filter model cards
    modelCards.forEach(card => {
        const text = card.textContent.toLowerCase();
        if (text.includes(query)) {
            card.style.display = '';
        } else {
            card.style.display = 'none';
        }
    });

    // Filter model list items
    modelListItems.forEach(item => {
        const text = item.textContent.toLowerCase();
        if (text.includes(query)) {
            item.style.display = '';
        } else {
            item.style.display = 'none';
        }
    });

    // Hide empty sections
    document.querySelectorAll('.model-list-section').forEach(section => {
        const visibleItems = section.querySelectorAll('li:not([style*="display: none"])');
        section.style.display = visibleItems.length > 0 ? '' : 'none';
    });
}

/**
 * Handle sensor search
 */
function handleSensorSearch() {
    const input = document.getElementById('sensorSearch');
    if (!input) return;

    const query = input.value.trim().toLowerCase();
    const sensorCards = document.querySelectorAll('.sensor-card');

    if (query.length === 0) {
        sensorCards.forEach(card => card.style.display = '');
        return;
    }

    sensorCards.forEach(card => {
        const text = card.textContent.toLowerCase();
        if (text.includes(query)) {
            card.style.display = '';
        } else {
            card.style.display = 'none';
        }
    });
}

/**
 * Look up a specific code
 */
function lookupCode(code) {
    if (!code) return;

    const cleanCode = code.trim().toUpperCase();

    // Validate code format
    const codePattern = /^[PBCU][0-9A-F]{4}$/i;
    if (!codePattern.test(cleanCode)) {
        alert('Please enter a valid OBD-II code (e.g., P0300, B1234, C0001, U0100)');
        return;
    }

    // Navigate to code page
    window.location.href = `pages/codes/${cleanCode}.html`;
}

/**
 * Initialize quick code lookup on homepage
 */
function initCodeLookup() {
    const lookupForm = document.getElementById('codeLookupForm');
    if (lookupForm) {
        lookupForm.addEventListener('submit', function(e) {
            e.preventDefault();
            const input = document.getElementById('quickCodeInput');
            if (input) {
                lookupCode(input.value);
            }
        });
    }
}

/**
 * Mobile menu toggle
 */
function initMobileMenu() {
    const menuToggle = document.querySelector('.navbar-toggle');
    const navMenu = document.querySelector('.navbar-menu');

    if (menuToggle && navMenu) {
        menuToggle.addEventListener('click', function() {
            navMenu.classList.toggle('active');
            menuToggle.classList.toggle('active');
        });

        // Close menu when clicking outside
        document.addEventListener('click', function(e) {
            if (!e.target.closest('.navbar-container')) {
                navMenu.classList.remove('active');
                menuToggle.classList.remove('active');
            }
        });
    }
}

/**
 * Smooth scroll for anchor links
 */
function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function(e) {
            const href = this.getAttribute('href');
            if (href === '#') return;

            const target = document.querySelector(href);
            if (target) {
                e.preventDefault();
                const navbarHeight = document.querySelector('.navbar')?.offsetHeight || 0;
                const targetPosition = target.getBoundingClientRect().top + window.pageYOffset - navbarHeight - 20;

                window.scrollTo({
                    top: targetPosition,
                    behavior: 'smooth'
                });
            }
        });
    });
}

/**
 * Check authentication state
 */
function checkAuthState() {
    const user = localStorage.getItem('user');
    const navbarActions = document.querySelector('.navbar-actions');

    if (user && navbarActions) {
        try {
            const userData = JSON.parse(user);
            navbarActions.innerHTML = `
                <a href="pages/profile/index.html" class="btn btn-secondary">My Account</a>
                <button class="btn btn-outline" onclick="logout()">Log Out</button>
            `;
        } catch (e) {
            console.error('Error parsing user data:', e);
        }
    }
}

/**
 * Logout function
 */
function logout() {
    localStorage.removeItem('user');
    window.location.reload();
}

/**
 * Debounce utility function
 */
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Format number with commas
 */
function formatNumber(num) {
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/**
 * Copy code to clipboard
 */
function copyCode(code) {
    navigator.clipboard.writeText(code).then(function() {
        showToast('Code copied to clipboard!');
    }).catch(function(err) {
        console.error('Failed to copy:', err);
    });
}

/**
 * Show toast notification
 */
function showToast(message, type = 'success') {
    // Remove existing toasts
    const existingToast = document.querySelector('.toast');
    if (existingToast) {
        existingToast.remove();
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;

    document.body.appendChild(toast);

    // Trigger animation
    setTimeout(() => toast.classList.add('show'), 10);

    // Remove after 3 seconds
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

/**
 * Intersection Observer for animations
 */
function initScrollAnimations() {
    const animatedElements = document.querySelectorAll('.animate-on-scroll');

    if ('IntersectionObserver' in window) {
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('animated');
                    observer.unobserve(entry.target);
                }
            });
        }, {
            threshold: 0.1
        });

        animatedElements.forEach(el => observer.observe(el));
    } else {
        // Fallback for older browsers
        animatedElements.forEach(el => el.classList.add('animated'));
    }
}

/**
 * Handle URL parameters (for search from other pages)
 */
function handleUrlParams() {
    const urlParams = new URLSearchParams(window.location.search);
    const searchQuery = urlParams.get('search');

    if (searchQuery) {
        const searchInputs = document.querySelectorAll('.search-input');
        searchInputs.forEach(input => {
            input.value = searchQuery;
            input.dispatchEvent(new Event('input'));
        });
    }
}

// Run URL param handler
document.addEventListener('DOMContentLoaded', handleUrlParams);

/**
 * Theme toggle (for future dark mode support)
 */
function toggleTheme() {
    const body = document.body;
    const currentTheme = body.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';

    body.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
}

/**
 * Initialize theme from localStorage
 */
function initTheme() {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme) {
        document.body.setAttribute('data-theme', savedTheme);
    }
}

// Initialize theme on load
initTheme();
