import {renderMain} from "../main.jsx";
import {renderStats} from "../stats.jsx";
import {renderOrganization} from "../organization.jsx";
import {renderOrganizations} from "../organizations.jsx";
import {renderAchievement} from "../achievement.jsx";
import {renderAchievements} from "../achievements.jsx";
import {renderLogout} from "../logout.jsx";
import {renderLogin} from "../login.jsx";
import {renderError} from "../error.jsx";
import {renderLoginCreateAccount} from "../login.create-account.jsx";

export function navigateTo(appPath) {
    window.location.hash = '#' + appPath;
}

export function hashChangeHandler() {
    // On every hash change the render function is called with the new hash.
    // This is how the navigation of our app happens.

    const appPath = decodeURI(window.location.hash.substr(1));

    renderRoute(appPath);
}

function renderRoute(appPath) {
    const appPathParams = parseHash(appPath);

    const routes = {
        'stats': renderStats,
        'logout': renderLogout,
        'login': renderLogin,
        'login-create-account': renderLoginCreateAccount,
        'organizations': renderOrganizations,
        'organizations/*': renderOrganization,
        'achievements': renderAchievements,
        'achievements/*': renderAchievement,
        '': renderMain
    };

    for (let routePattern in routes) {
        if (isPathMatch(appPathParams, routePattern)) {
            const routeRenderer = routes[routePattern];
            routeRenderer.call(this, appPathParams);
            return;
        }
    }
    renderError(appPath + " does not exist.");
}

function isPathMatch(pathComponents, pattern) {
    const patternComponents = parseHash(pattern);
    if (patternComponents.length != pathComponents.length) {
        return false;
    }
    for (let i = 0; i < patternComponents.length; i++) {
        if (patternComponents[i].resource != pathComponents[i].resource) {
            return false;
        }
        if (patternComponents[i].key != '*' && pathComponents[i].key != patternComponents[i].key) {
            return false;
        }
    }
    return true;
}

function parseHash(urlHash) {
    const pathComponents = [];
    const parts = urlHash.split(/\//);
    for (let i = 0; i < parts.length; i += 2) {
        pathComponents.push({resource: parts[i], key: parts[i + 1] || ""});
    }
    return pathComponents;
}
