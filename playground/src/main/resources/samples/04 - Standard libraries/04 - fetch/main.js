// fetch(input, init) from the fetch library: a promise of a Response, with
// Headers, Request and Response as the WHATWG specification has them. The
// request runs on the JDK's HttpClient and the promise settles on the
// script's thread through the event loop, so async/await reads naturally.
// These calls go to public APIs that need no key - so this sample needs
// the network, and says so if it has none.

// Current weather in Paris, from Open-Meteo (https://open-meteo.com)
async function weather(city, latitude, longitude) {
    var response = await fetch('https://api.open-meteo.com/v1/forecast?latitude=' + latitude + '&longitude=' + longitude + '&current_weather=true');
    print(city + ':', response.status, response.statusText, '-', response.headers.get('content-type'));
    var data = await response.json();
    var now = data.current_weather;
    print('  ' + now.temperature + ' ' + data.current_weather_units.temperature + ', wind ' + now.windspeed + ' ' + data.current_weather_units.windspeed + ', at ' + now.time);
}

// A GitHub repository, from the GitHub REST API (60 unauthenticated calls an hour)
async function repository(name) {
    var response = await fetch('https://api.github.com/repos/' + name, { headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'nashorn-playground' } });
    if (!response.ok) {                     // an HTTP error resolves - ok says whether it was 2xx
        print(name + ':', response.status, response.statusText, '-', (await response.json()).message);
        return;
    }
    var repo = await response.json();
    print(name + ':', repo.description);
    print('  ' + repo.stargazers_count + ' stars, ' + repo.forks_count + ' forks, default branch ' + repo.default_branch + ', updated ' + repo.updated_at);
}

(async function () {
    try {
        await weather('Paris', 48.85, 2.35);
        await weather('Tokyo', 35.68, 139.69);

        await repository('openjdk/nashorn');
        await repository('openjdk/no-such-repository');   // a 404: resolved, with ok false

        // several requests in flight at once
        var started = Date.now();
        var cities = await Promise.all([
            fetch('https://api.open-meteo.com/v1/forecast?latitude=40.71&longitude=-74.01&current_weather=true'),
            fetch('https://api.open-meteo.com/v1/forecast?latitude=-33.87&longitude=151.21&current_weather=true'),
            fetch('https://api.open-meteo.com/v1/forecast?latitude=51.51&longitude=-0.13&current_weather=true')
        ]);
        var temperatures = [];
        for (var response of cities) {
            temperatures.push((await response.json()).current_weather.temperature);
        }
        print('New York, Sydney, London at once:', temperatures.join(' / '), 'in', Date.now() - started, 'ms');

        // Headers: case-insensitive, several values joined
        var headers = new Headers({ 'Accept': 'application/json' });
        headers.append('accept', 'text/plain');
        print('Headers are case-insensitive and joined:', headers.get('ACCEPT'));
    } catch (e) {
        // a network or DNS failure rejects with a TypeError
        print('No network? fetch rejected with', e.name + ':', e.message);
    }
    try {
        await fetch('http://127.0.0.1:1/nothing-listens-here');
    } catch (e) {
        print('A connection refused rejects too:', e.name, '-', e.message);
    }
})();

print('fetch calls are in flight; the run ends when they have all settled');
