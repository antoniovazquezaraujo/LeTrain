
<a name="readme-top"></a>

<p align="left">
  🌐 <strong>English</strong> | <a href="README_es.md">Español</a>
</p>

[![CI][ci-shield]][ci-url]
[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![Apache 2.0 License][license-shield]][license-url]

```
     __       ______           _
    / /   ___/_  __/________ _(_)___
   / /   / _ \/ / / ___/ __ `/ / __ \
  / /___/  __/ / / /  / /_/ / / / / /
 /_____/\___/_/ /_/   \__,_/_/_/ /_/
      The Letter Train Simulator
  (C) 2006-2026 Antonio Vazquez Araujo
```

<!-- PROJECT LOGO -->
<br />
<div align="center">

<h1 align="center">LeTrain</h1>

  <p align="center">
    LeTrain is a procedural train simulator with a classic ASCII terminal client and a modern 3D client
    <br />
    <a href="https://antoniovazquezaraujo.github.io/LeTrain/"><strong>Explore the docs »</strong></a>
    <br />
    <br />
    <a href="https://github.com/antoniovazquezaraujo/LeTrain/releases/latest">Download</a>
    ·
    <a href="https://avaraujo.itch.io/letrain-procedural-tycoon">Play on itch.io</a>
    ·
    <a href="https://snapcraft.io/letrain">Snap Store</a>
    ·
    <a href="https://github.com/antoniovazquezaraujo/LeTrain/issues">Report Bug</a>
    ·
    <a href="https://github.com/antoniovazquezaraujo/LeTrain/issues">Request Feature</a>
  </p>
</div>



<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
        <li><a href="#configuration">Configuration</a></li>
      </ul>
    </li>
    <li>
      <a href="#usage">Usage</a>
      <ul>
        <li><a href="#economy">Economy</a></li>
      </ul>
    </li>
    <li><a href="#known-issues">Known Issues</a></li>
    <li><a href="#roadmap">Roadmap</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>


# Warning: Human-generated content

Hello. I'm Antonio, the author of LeTrain. The previous version of this file was written with AI agents. A comment on Hacker News made me realize that wasn't a good idea.
I realized that the “ME” in “README” doesn't refer to the file—it refers to the project's author.
A project’s first point of contact can’t be just technical information—that’s what manuals and tutorials are for—it must be an invitation to talk, a human introduction; it can’t give the impression that there’s no one at the helm (which has been happening lately with many projects resulting from a week of “vibe-coding”).

LeTrain has been on my mind since 2006; the first version was in C++. All the ideas, mechanisms, algorithms, and bugs are my responsibility. And, after years of watching it grow, I’m now seeing that everything in it truly reflects my style, my way of seeing things, and my personality.
I’ve been learning from it every day since then, and I still have a lot to learn.

In March 2026, I started using AI—just like everyone else (well, except maybe that 1% of human programmers who are still better than LLMs :-D). I use OpenCode as my agent harness and built a team of agents specialized in each part of the project. I also gave them human names—specifically, the names of the people on my team from when I worked at Imatia—perhaps so I wouldn’t miss them, or perhaps to change my way of working as little as possible. Dani is the coordinator, Alex is a Java expert, Jorge is a whiz at the UI, and I made up a new one, Bicho, who’s an expert in testing.

I talk to them as if they were people—or as if they were “silicon people.” The truth is, they show great responsibility and even a good sense of humor. Sometimes we argue; sometimes I correct them, and other times they correct me. Frankly, I don’t feel like I’m losing control of the project. Ultimately, I’m still the one making every important decision, and I still know what I want (and what I don’t want).

But it’s true that I no longer write source code—at least not Java code—and I don't review it line by line. What I review is the product and its behavior. I went from being the project’s “jack-of-all-trades” to being the Architect. Actually, I used to trust the compiler back then, too; I didn’t review the bytecode it generated. It’s true that AI can be unpredictable at times and create real monsters, which is why it’s important to use a good safety net, make sure everything is tested, and above all, ensure that the foundation on which we’re working is robust and clear. This is what I’ve focused on all these years: everything is separated, standard programming patterns are used, and the codebase was very clear. With that in place, AI or a human can make changes, improvements, and enhancements without fear of breaking anything.

But that comment made me realize the value of being human. Even if my writing isn’t that great and my English needs Deep Translator, people value information more when it comes from another human being. We don’t like feeling like we’re “talking to a toaster.”

I’ll continue working as a team with my “invisible friends” to improve the project, but I’ll also gladly accept help from other people. I promise to respond personally and not hide behind technical jargon.

Now, here is the old `README`, but updated to the current status. If you want to know why this exists, the [manifesto](docs/PHILOSOPHY.md) explains it more calmly.

Antonio.

<!-- ABOUT THE PROJECT -->
## About The Project
LeTrain is a procedural train simulator with a classic ASCII terminal client and a modern 3D client. It consists of creating tracks and stations along a virtually infinite map, creating trains and earning money by carrying cargo (gold, coal and ruby) between producers and consumers. The farther apart they are, the more profitable the trip.

**Warning**
This is a preliminary version. There are still many things that are pending. At the moment it works but one day it will be much better than it is now.

![LeTrain map zoomed out](images/zoom-out.png)

<p align="right">(<a href="#readme-top">back to top</a>)</p>


### Built With
* Java OpenJDK 17
* [Lanterna library](https://github.com/mabe02/lanterna) (2D terminal client)
* [LibGDX](https://libgdx.com/) (3D client)
* ANTLR4 (rail automation scripting language)
* Granular audio synthesis engine

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- GETTING STARTED -->
## Getting Started

### Prerequisites

* Java JDK 17
* Apache Maven

### Installation
If you don't want to build it, the latest release is on [GitHub](https://github.com/antoniovazquezaraujo/LeTrain/releases/latest), and there are also versions on [itch.io](https://avaraujo.itch.io/letrain-procedural-tycoon) and the [Snap Store](https://snapcraft.io/letrain).

To install it:
* Clone it
   ```sh
   git clone https://github.com/antoniovazquezaraujo/LeTrain.git
   ```
* Build it
	```sh
	mvn clean package -DskipTests
	```
* Run it
	```sh
	output/LeTrain/bin/LeTrain.sh      # 3D
	output/LeTrain2D/bin/LeTrain2D.sh  # 2D terminal
	```

### Configuration
LeTrain reads `letrain.cfg`, which ships next to the launcher. You can edit it by hand to change costs, rewards, the length of the day and other settings. A `letrain.cfg` in the working directory takes precedence over the packaged one.

You can also change most things from the in-game **Settings** dialog (File → Settings). Some changes apply immediately, others ask you to reapply them, and a few need a new game; the dialog tells you which is which. If you export a scenario, its `configuration { ... }` block carries the settings with it and wins over the local `letrain.cfg`, so the map plays with the same rules for everyone.

![LeTrain settings](images/settings.png)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- USAGE EXAMPLES -->
## Usage

### Economy
You start with $0. That's not a bug: the idea is to build something from nothing, and I like it that way. The map has producers (mines) and consumers (cities and factories), and you earn money by carrying cargo (gold, coal and ruby) from one to the other. A load station next to a mine fills the wagons; an unload station next to a city or factory empties them and pays you.

The further the cargo travels, the more it pays. But distance also costs: every meter the train moves burns fuel, and bridges and tunnels are much more expensive than plain track. A long route can be very profitable or ruin you, depending on how you build it.

You can adjust the base prices and rewards in `letrain.cfg`, or put your own values in a scenario so the rules travel with the map. The number at the bottom right of the menu is your balance.

### Ground, Water and Mountains
This is the general appearance of LeTrain. On the map, randomly generated for each game, you can see water in blue, fields in green and mountains in brown. Building tracks across normal ground is fast and cheap. Creating bridges to cross lakes or drilling tunnels to cross mountains, however, is slow and expensive.

![LeTrain map up close](images/zoom-in.png)

At the bottom you can see the menu, where the different game modes are selected, which will allow us to create tracks, build wagons and locomotives, forks, speed signals, semaphores, and sensors.

Each option is activated with the letter that appears in a different color, for example, to enter "drive" mode use the "d" key.

To create tracks on the map just select the "rails" mode and move with the cursor. Holding down the shift key will generate tracks and with control you can delete them. Forks and crossings are created automatically.

Once we have tracks, we just have to go to "trains" mode, which will allow us to create wagons (any lower case letter) and locomotives (any upper case letter).
**To exit this mode you need to press Enter**.

The keys are the same in both clients; the 3D client adds mouse and camera controls for zoom and view.

This is the same world in the 3D client:

![LeTrain in 3D](images/3D-example.png)

![Creating tracks](images/creating-tracks.gif)

#### Day and night
The game has a clock, and the light changes with it. At night the palette turns darker and the head locomotive lights the track with its headlights.

![LeTrain at night](images/night.png)

### Linking and unlinking
When creating wagons and locomotives they do not form a train, they are unlinked. To create a train you need to have the locomotive next to them and select "link" and then with the vertical keys, choose the direction to look for wagons and with the horizontal ones the number of elements to link. Then, with the space bar you will link the selected wagons. That will be a train. To unlink wagons, select "unlink", select the direction and move forward or backward to the wagon where you want to split. Then use the space bar.

![Link and unlink](images/link-and-unlink.gif)

### Infinite map
If you leave the page with the cursor, you will see that the map continues. There is no limit but memory.

If you find that the cursor is moving too slowly, you can type any number before using it, and each step will be added to that number of steps. For example, if you type 30, each time you press the advance key, 31 steps will be advanced, the default step plus the other 30. (If you are a Vim user -cheers Bram, wherever you are- you will be familiar with this kind of quantifier). To return to the default quantifier, just use the space bar.

![Inside a tunnel](images/inside-tunnel.png)

### Programmable behavior
Everything can be programmed, usually based on events—such as a train passing through a station or triggering a sensor—and actions can be performed as a result, such as coupling wagons, starting the train, loading cargo, moving track switches, or changing signals.

![LeTrain editor](images/commands.png)

### The editor
Press `p` to open the LeTrain editor. It has three tabs: **Scenario** (the seed, the initial build and the starting state), **Programming** (the automation script) and **Config** (the game settings). The right side has a quick reference for the active tab, and if something is wrong, the error list takes you to the line.

From the editor you can export and import `.ltr` scenarios, reload the current world, apply the program or rebuild the world from the scenario. `Esc` closes it. Save and load live in the File menu of the game, not in the editor.

### Record, undo and experiment

While you build, `R` (`Shift+R`) toggles record/edit mode: the world freezes, you build instantly, and every edit goes into a journal. `u` undoes the last edit and `Ctrl+R` redoes it. `X` (`Shift+X`) toggles experiment mode: it takes a snapshot, lets you try anything, and restores it when you leave.

In TRAINS mode, uppercase letters create locomotives, so `X` and `R` create a locomotive with that letter instead of toggling those modes.

### Saving the game
You can save the game at any time, but there is one exception: if a train is in the middle of a load, unload or wait, the pending action is not saved. Let it finish first. There are two different files, and it is worth knowing which one you need.

The first one is the savegame, a `.json` file with the current state of the world: where the trains are, what they are carrying, how much money you have and what time it is. Load it and you continue exactly where you left off.

The second one is the scenario, a plain-text `.ltr` file. It is not a snapshot of the game, but the recipe to rebuild it: the map seed, the game settings, the tracks and stations you built, the initial state and the program that automates the network. The same seed and the same commands give you the same world, so you can share it with someone else, open it in any text editor (or in the LeTrain editor) and check it with `letrain-check` before loading it.

Use `save` and `load` for savegames, and `export` and `import` for scenarios. One keeps the game as it is; the other keeps the recipe to build it again.

### Zoom
In 2D, you can use the console zoom keys to see more or less scenery when you need it. They are usually assigned to ctrl-+ and ctrl--.
You can also use the PgUp and PgDown keys to move up and down pages or ctrl+PgUp or ctrl+PgDown to move across pages horizontally.

In 3D, the camera can be oriented with `Alt+hjkl/cursors` and with the mouse wheel.

#### Don't get lost
With a big map, it is easy to get lost; to go back just switch to "drive" mode and select with the arrows the next or previous locomotive. The same with forks, stations, sensors, etc. You can also select objects by their number, just type the number and the object will be selected.

Also, if you want the cursor immediately placed at the position of an object, a fork, a station, a train, a semaphore, etc. simply press the `o` key to relocate it.

![Zoom](images/zoom.gif)

### Train crash
Yes: if two trains approach each other at a speed of 5 or more, a catastrophe will occur. You will lose the money you have spent to create them. The money is the number at the bottom right of the menu. It decreases when trains move, and when tracks are built. It increases when trains unload cargo at a consumer station.

![Train crash](images/train-crash.gif)

 _For more examples, please refer to the [Documentation](https://antoniovazquezaraujo.github.io/LeTrain/)_

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- KNOWN ISSUES -->
## Known Issues

The Snap package is convenient, but it's sandboxed and that causes problems in some environments. Under WSL2 it runs without sound, because it can't reach the PulseAudio socket that Windows exposes. Over XRDP or VNC, the 3D client usually fails with an OpenGL error (`Failed to create context: BadValue`), and the 2D client can't send audio through the remote session.

If you're in one of those cases, use the Linux zip (the `.sh` launcher) or build it from source. On Windows, the `LeTrain-Windows.zip` from the [releases page](https://github.com/antoniovazquezaraujo/LeTrain/releases/latest) is the best option. If you find another problem, open an issue and tell me.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- ROADMAP -->
## Roadmap

There are many things to do, but for the moment it works.
- [ ] Add passenger trains and cities
- [ ] Finish the event-driven train sound (in review)
- [ ] Add professionally recorded train sounds: real locomotives, wagons and stations
- [ ] Add track inspection, maintenance and failures
- [ ] Add train cleaning, maintenance and breakdowns

See the [open issues](https://github.com/antoniovazquezaraujo/LeTrain/issues) for a full list of proposed features (and known issues).

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- CONTRIBUTING -->
## Contributing

If you want to help, read [CONTRIBUTING.md](CONTRIBUTING.md) first: it explains the build, the tests and the branch/PR flow.

If you have an idea, open an issue. If you want to fix it yourself, fork the repo, create a branch and send a pull request. And if you just like the project, a star helps.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- LICENSE -->
## License

Distributed under the Apache License 2.0. See `LICENSE` for more information.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- CONTACT -->
## Contact


Antonio Vazquez Araujo

Twitter/X - [@avaraujo](https://twitter.com/avaraujo)

Gmail - antoniovazquezaraujo@gmail.com

Project Link: [https://github.com/antoniovazquezaraujo/LeTrain](https://github.com/antoniovazquezaraujo/LeTrain)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[ci-shield]: https://img.shields.io/github/actions/workflow/status/antoniovazquezaraujo/LeTrain/ci.yml?style=for-the-badge
[ci-url]: https://github.com/antoniovazquezaraujo/LeTrain/actions/workflows/ci.yml
[contributors-shield]: https://img.shields.io/github/contributors/antoniovazquezaraujo/LeTrain.svg?style=for-the-badge
[contributors-url]: https://github.com/antoniovazquezaraujo/LeTrain/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/antoniovazquezaraujo/LeTrain.svg?style=for-the-badge
[forks-url]: https://github.com/antoniovazquezaraujo/LeTrain/network/members
[stars-shield]: https://img.shields.io/github/stars/antoniovazquezaraujo/LeTrain.svg?style=for-the-badge
[stars-url]: https://github.com/antoniovazquezaraujo/LeTrain/stargazers
[issues-shield]: https://img.shields.io/github/issues/antoniovazquezaraujo/LeTrain.svg?style=for-the-badge
[issues-url]: https://github.com/antoniovazquezaraujo/LeTrain/issues
[license-shield]: https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge
[license-url]: https://github.com/antoniovazquezaraujo/LeTrain/blob/main/LICENSE
