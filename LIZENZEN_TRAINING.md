# Lizenzen der Songs für Training und Messung

Die Netze in der App (Gesangserkennung `VocalNet.h`, Beat-Erkennung `BeatNet.h`) sind **nur** mit Songs
unter **CC0, CC BY oder Public Domain** gelernt. Songs mit NC (nicht-kommerziell), ND (keine Bearbeitung),
SA (Weitergabe unter gleicher Lizenz), Sampling+ oder unklarer Lizenz werden nie zum Lernen benutzt.
Die Regel steht an einer Stelle im Code: `tools/realmusic/refs.py`, Funktion `training_allowed()`;
die Lern-Skripte (`train_vocal_net.py`, `train_beat_net.py`) benutzen nur diese Songs.

Die Audiodateien liegen **nicht** im Projekt – nur Titel, Interpret, Lizenz, Quelle und die daraus
berechneten Referenzen (Beat-Zeiten, Lautstärke-Hüllkurven).

- **Lern-Songs** (`songs_train.json`): eigens ausgewählt, alle CC BY oder CC0 (ccMixter).
- **Übungs-Songs** (`songs_dev.json`): zum Lernen nur die mit erlaubter Lizenz. Die übrigen dienen nur zum
  Prüfen und Abstimmen einzelner Grenzwerte (z. B. ab welcher Wahrscheinlichkeit Gesang zählt) – sie werden
  analysiert und gemessen, aber nicht in die Netze gelernt.
- **Test-Songs** (`songs.json`): nur zum Messen der Ergebnisse (Bericht), nie zum Lernen oder Abstimmen.

**Zusammen 118 Songs zum Lernen der App-Netze** (alle CC0/CC BY/PD).

## Lern-Songs (nur CC0/CC-BY): 100 Songs, davon 100 zum Lernen

Lizenzen: CC BY 3.0 (75), CC BY 2.5 (18), CC0 1.0 (7)

| ID | Titel | Interpret | Genre | Lizenz | Lernen | Quelle |
|---|---|---|---|---|---|---|
| t001 | Put Your Hands Up | Coruscate | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/Coruscate/35893 |
| t002 | Watching Seabirds Soar (Harmony) | DoKashiteru | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/DoKashiteru/24925 |
| t003 | Homesick (Keymix) | Keytronic | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/Keytronic/47457 |
| t004 | Maybe | myfreemickey | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/myfreemickey/41313 |
| t005 | Sola Solita | mykleanthony | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/mykleanthony/20638 |
| t006 | Falling | Zutsuri | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/Zutsuri/55541 |
| t007 | Spinning Merkaba* | anchormejans | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/anchormejans/19894 |
| t008 | The Drums | morgantj | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/morgantj/32182 |
| t009 | Dubbish Rubbish Christmas | morgantj | Andere | CC BY 3.0 | ja | https://ccmixter.org/files/morgantj/24050 |
| t010 | Vertigo | shimoda | Andere | CC0 1.0 | ja | https://ccmixter.org/files/shimoda/55042 |
| t011 | Drunk With You | Allerlei_von_Nicolai | House | CC BY 3.0 | ja | https://ccmixter.org/files/Allerlei_von_Nicolai/60752 |
| t012 | I Miss You (asha remix) | asha | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/asha/45745 |
| t013 | Progression Pella | axton | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/axton/29595 |
| t014 | Troubled Times ft Spinningmerkaba | basematic | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/basematic/31121 |
| t015 | Slipping | chandubaba | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/chandubaba/27035 |
| t016 | Language of My Reality | commonsplice | Techno | CC BY 2.5 | ja | https://ccmixter.org/files/commonsplice/836 |
| t017 | PUT U HANDS UP | Crunkster | House | CC BY 3.0 | ja | https://ccmixter.org/files/Crunkster/38231 |
| t018 | Don't You Think That I | djlang59 | House | CC BY 3.0 | ja | https://ccmixter.org/files/djlang59/62036 |
| t019 | In Peace, The Love & Happiness Mix | djlang59 | House | CC BY 3.0 | ja | https://ccmixter.org/files/djlang59/33696 |
| t020 | Pandemonium | Duco | House | CC BY 3.0 | ja | https://ccmixter.org/files/Duco/52898 |
| t021 | Keep The Party Rocking | F_Fact | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/F_Fact/46526 |
| t022 | Music Through Love | mana_junkie | House | CC BY 3.0 | ja | https://ccmixter.org/files/mana_junkie/39605 |
| t023 | Hurricane (with MissJudged) | mana_junkie | House | CC BY 3.0 | ja | https://ccmixter.org/files/mana_junkie/50723 |
| t024 | Dancing Round Like Dandelion (hybrid dance mix) | party_redlight | House | CC BY 3.0 | ja | https://ccmixter.org/files/party_redlight/50498 |
| t025 | DirtBag (Club Mix) | scomber | House | CC BY 2.5 | ja | https://ccmixter.org/files/scomber/17734 |
| t026 | This way >> | scomber | House | CC BY 3.0 | ja | https://ccmixter.org/files/scomber/45961 |
| t027 | Go to Girl (Adilyn's Mix) | snowflake | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/snowflake/30788 |
| t028 | Used Me Vocals | Thespinwires | House | CC BY 3.0 | ja | https://ccmixter.org/files/Thespinwires/45241 |
| t029 | Brad Sucks - Never Get Out ( Doubtful Remix ) | 76deep | House | CC BY 2.5 | ja | https://ccmixter.org/files/76deep/55207 |
| t030 | Analog By Nature - Are You Ready | AnalogByNature | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/AnalogByNature/41473 |
| t031 | Sensation | Bluemillenium | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/Bluemillenium/54833 |
| t032 | Morsbach,1954. | Bluemillenium | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/Bluemillenium/35145 |
| t033 | Seven(New Music Remix) | bwatts | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/bwatts/39793 |
| t034 | Our Music - RumbleStep Mix | cdk | House | CC BY 3.0 | ja | https://ccmixter.org/files/cdk/39415 |
| t035 | Snaps (Original RumbleStep Mix) | cdk | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/cdk/47643 |
| t036 | Coming Up For Air | Coruscate | House | CC0 1.0 | ja | https://ccmixter.org/files/Coruscate/44888 |
| t037 | Tiranny of War | Dimensional_Pulse | Techno | CC BY 3.0 | ja | https://ccmixter.org/files/Dimensional_Pulse/70881 |
| t038 | Deep Unity (Essential Deeper Remix) | DJ_Essential_I | House | CC BY 3.0 | ja | https://ccmixter.org/files/DJ_Essential_I/19878 |
| t039 | Again, Once Again | fluffy | House | CC BY 3.0 | ja | https://ccmixter.org/files/fluffy/53739 |
| t040 | Dummy | Ghost_k | House | CC BY 2.5 | ja | https://ccmixter.org/files/Ghost_k/39113 |
| t041 | World Of Sports | JeffSpeed68 | House | CC BY 3.0 | ja | https://ccmixter.org/files/JeffSpeed68/53359 |
| t042 | Drop My Raga Flow | Killbotz | House | CC BY 2.5 | ja | https://ccmixter.org/files/Killbotz/11005 |
| t043 | Amari wakaranai kedo | kulimu | Techno | CC BY 2.5 | ja | https://ccmixter.org/files/kulimu/13575 |
| t044 | Kalte Ohren (Cold Ears) Stem pack | starfrosch | House | CC BY 3.0 | ja | https://ccmixter.org/files/starfrosch/59511 |
| t045 | U_F_O " F I X I N G  M Y  B R A I N " ( Final ReMix ) | U_F_O | House | CC BY 2.5 | ja | https://ccmixter.org/files/U_F_O/8946 |
| t046 | Toronto Is My Beat | Whitewolf225 | House | CC BY 3.0 | ja | https://ccmixter.org/files/Whitewolf225/60181 |
| t047 | 3 Funky Kings | admiralbob77 | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/admiralbob77/64415 |
| t048 | Morning Star | AlexisSofield | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/AlexisSofield/44292 |
| t049 | More Than 2 sides (Gazzy d REmix) | beerbelly30 | Pop | CC BY 2.5 | ja | https://ccmixter.org/files/beerbelly30/22474 |
| t050 | Put the Needle Down (Groovy Mix) | commmorancy | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/commmorancy/28279 |
| t051 | Time To Take Out The Trash | dotjot | Pop | CC BY 2.5 | ja | https://ccmixter.org/files/dotjot/37452 |
| t052 | You can't deny | E_SoX | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/E_SoX/35762 |
| t053 | Sick as a dog | Finger050 | Pop | CC BY 2.5 | ja | https://ccmixter.org/files/Finger050/29499 |
| t054 | Ain't it Sad | frog1999 | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/frog1999/27921 |
| t055 | mag-admiralbob77--fading things | magmavander | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/magmavander/36833 |
| t056 | Get Out to Vote | mindmapthat | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/mindmapthat/58309 |
| t057 | Harmony | Mseq | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/Mseq/28461 |
| t058 | Ophelia's Song (Vocals) | musetta | Pop | CC BY 2.5 | ja | https://ccmixter.org/files/musetta/6343 |
| t059 | all the time (dogmix) | oldDog | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/oldDog/24618 |
| t060 | Rescue Me | oldDog | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/oldDog/17747 |
| t061 | move along | pneuman | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/pneuman/32715 |
| t062 | with U | raja_ffm | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/raja_ffm/22559 |
| t063 | Magic Moon - Hold me close (vocal funk mix) | RobbH | Pop | CC0 1.0 | ja | https://ccmixter.org/files/RobbH/32295 |
| t064 | Persephone (robomix) | robomusic | Pop | CC BY 2.5 | ja | https://ccmixter.org/files/robomusic/23242 |
| t065 | Fight The Blues For Christmas (2021) | speck | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/speck/64470 |
| t066 | Slow Motion Dream | stevieb357 | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/stevieb357/34740 |
| t067 | StarWar | thespiritoflight | Pop | CC BY 2.5 | ja | https://ccmixter.org/files/thespiritoflight/13615 |
| t068 | Alice In The City (Club Version) | xeden_12081968 | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/xeden_12081968/44385 |
| t069 | Funky Christmas Blues | admiralbob77 | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/admiralbob77/64416 |
| t070 | Out of Step (stems) | assoverteakettle | Pop | CC0 1.0 | ja | https://ccmixter.org/files/assoverteakettle/39100 |
| t071 | Grace_djollief and luxmusic | luxmusic | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/luxmusic/39539 |
| t072 | Lucid | RizkeyG | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/RizkeyG/56677 |
| t073 | Clavinometron | VJ_Memes | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/VJ_Memes/33119 |
| t074 | Ol' Man Clothes | VJ_Memes | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/VJ_Memes/33403 |
| t075 | Capitalism Song (Paddle Your Own Canoe) | andrewwainwright | Rock | CC0 1.0 | ja | https://ccmixter.org/files/andrewwainwright/55941 |
| t076 | Where You Are Now | Benjamin_Orth | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/Benjamin_Orth/33036 |
| t077 | Put the Needle Down | Benjamin_Orth | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/Benjamin_Orth/32277 |
| t078 | Sick as a Dog (vocals) | bradsucks | Rock | CC BY 2.5 | ja | https://ccmixter.org/files/bradsucks/3551 |
| t079 | Will You Share With Me | donniedrost | Rock | CC BY 2.5 | ja | https://ccmixter.org/files/donniedrost/28147 |
| t080 | Pleasure in Love | Fireproof_Babies | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/Fireproof_Babies/31149 |
| t081 | Ahi Vienen (Country Version) | Funnywico | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/Funnywico/45845 |
| t082 | Cases of Misused Apostrophe's | go1dfish | Rock | CC BY 2.5 | ja | https://ccmixter.org/files/go1dfish/29125 |
| t083 | Chaos and Corruption | jaspertine | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/jaspertine/60696 |
| t084 | I don't Want to go To Sleep | JeffSpeed68 | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/JeffSpeed68/45184 |
| t085 | Political Prisoner (feat. My Free Mickey) | jlbrock44 | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/jlbrock44/41318 |
| t086 | Stay | mykleanthony | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/mykleanthony/33209 |
| t087 | Overreacting | Neurowaxx | Rock | CC BY 2.5 | ja | https://ccmixter.org/files/Neurowaxx/13937 |
| t088 | IA IA! Our Hero! | PorchCat | Rock | CC0 1.0 | ja | https://ccmixter.org/files/PorchCat/7381 |
| t089 | Rock Version - How Do I Make You See | ramblinglibrarian | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/ramblinglibrarian/25641 |
| t090 | Again and Again | TheDICE | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/TheDICE/37674 |
| t091 | Preview of new album "Alone At Night" from Unden Leslie | undenleslie | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/undenleslie/59707 |
| t092 | Holy is the Lord | unreal_dm | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/unreal_dm/24951 |
| t093 | Unbutton | Alex_J_Stephens | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/Alex_J_Stephens/59224 |
| t094 | Nightmares | donniedrost | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/donniedrost/38782 |
| t095 | Break It Down - Stems | doxent | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/doxent/41321 |
| t096 | The Stars Look Different (Ziggy Stardust Mix) | jlbrock44 | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/jlbrock44/52765 |
| t097 | Feet Gone Wild Slow Version | Monplaisir | Rock | CC0 1.0 | ja | https://ccmixter.org/files/Monplaisir/56218 |
| t098 | Dirtbag Chorus remix | Mseq | Rock | CC BY 2.5 | ja | https://ccmixter.org/files/Mseq/21969 |
| t099 | I Have Often Told You Stories (guitar instrumental) | ramblinglibrarian | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/ramblinglibrarian/41284 |
| t100 | Alternator | RobertWarrington | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/RobertWarrington/31783 |

## Übungs-Songs (Abstimmen; Lernen nur mit erlaubter Lizenz): 102 Songs, davon 18 zum Lernen

Lizenzen: CC BY-NC 3.0 (48), CC BY-NC-ND 3.0 (9), CC BY 3.0 (8), unklar (8), CC BY 4.0 (7), CC BY-NC-ND 4.0 (4), CC BY-NC-SA 3.0 (4), CC BY-NC 4.0 (3), CC BY 2.5 (2), CC BY-ND 4.0 (2), CC BY-SA 4.0 (2), NC Sampling+ 1.0 (1), Sampling+ 1.0 (1), CC0 1.0 (1), CC BY-ND 2.0 (1), CC BY-NC-ND 2.5 (1)

| ID | Titel | Interpret | Genre | Lizenz | Lernen | Quelle |
|---|---|---|---|---|---|---|
| d01 | U TERU S . Birth of Souls | Loveshadow | Pop | NC Sampling+ 1.0 | nein | https://ccmixter.org/files/Loveshadow/23570 |
| d02 | Spinnin' | AlexBeroza | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/AlexBeroza/32423 |
| d03 | Sooner Or Later (Acappella) | trifonic | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/trifonic/14568 |
| d04 | THE Reason ! ( Somthin Special) | Loveshadow | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Loveshadow/24203 |
| d05 | Homesick | kizzylotus | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/kizzylotus/47456 |
| d06 | Broken (DURDEN version) | DURDEN | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/DURDEN/15248 |
| d07 | Madrugada a la Gil Evans | waldhorn33 | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/waldhorn33/11246 |
| d08 | Shame (dogmix) | oldDog | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/oldDog/15276 |
| d09 | Certain Death (Still Alive Remix) | Blackberry | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Blackberry/17059 |
| d10 | Fever | snowflake | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/snowflake/52149 |
| d11 | Drive | AlexBeroza | Rock | CC BY 3.0 | ja | https://ccmixter.org/files/AlexBeroza/43098 |
| d12 | Old Chucks | djlang59 | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/djlang59/34448 |
| d13 | Crazy Love-The  Alex & Lang mix | djlang59 | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/djlang59/10579 |
| d14 | I Fell Out of the Sky | DoKashiteru | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/DoKashiteru/17807 |
| d15 | goodbye butterfly | VJ_Memes | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/VJ_Memes/43752 |
| d16 | Heartstring Hotel | Skill_Borrower | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Skill_Borrower/41444 |
| d17 | Le Illusioni | Javolenus | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Javolenus/40795 |
| d18 | 12 String | Skill_Borrower | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Skill_Borrower/41576 |
| d19 | AIMEE / STEPHANIE RMX | BOCrew | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/BOCrew/24141 |
| d20 | Portland Firebloom | Super_Sigil | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Super_Sigil/41038 |
| d21 | In Peace Now | Super_Sigil | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Super_Sigil/40614 |
| d22 | Communication | commmorancy | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/commmorancy/28151 |
| d23 | Catch My Fall | psychadelik_pedestrian | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/psychadelik_pedestrian/42578 |
| d24 | Dreaming | mofox123 | Pop | CC BY 3.0 | ja | https://ccmixter.org/files/mofox123/43031 |
| d25 | Elizabeth June | mouz | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/mouz/33690 |
| d26 | Overwhelming Emotions | Vidian | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Vidian/41665 |
| d27 | Air (Re-upload with Vocal + Instrumental Stems) | coffeeeurope | Pop | CC BY 2.5 | ja | https://ccmixter.org/files/coffeeeurope/44798 |
| d28 | Just Drive feat. Team Smile And Nod | semisweet | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/semisweet/46018 |
| d29 | Natchoongi | whytong | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/whytong/39507 |
| d30 | Got To rock (el_Wis remix) | El_Wis | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/El_Wis/40269 |
| d31 | circeling | einneb61 | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/einneb61/45383 |
| d32 | Just Drive | starfrosch | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/starfrosch/39132 |
| d33 | Parametaphoriquement | gmz | House | CC BY 3.0 | ja | https://ccmixter.org/files/gmz/19367 |
| d34 | Nadeya | mentorrecords | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/mentorrecords/12984 |
| d35 | It Is the Hour (Get Up) | snowflake | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/snowflake/29123 |
| d36 | pROgraM vs. Us3R | morgantj | House | CC BY 3.0 | ja | https://ccmixter.org/files/morgantj/30328 |
| d37 | Abundance | SackJo22 | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/SackJo22/47133 |
| d38 | Let's Sing (ft. George Ellinas with Snowflake) | SackJo22 | House | CC BY 3.0 | ja | https://ccmixter.org/files/SackJo22/38362 |
| d39 | O N E  T H I N G (Dance in the Rain Mashup) | thatcrazylittleasian | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/thatcrazylittleasian/62228 |
| d40 | Love Is What's Missing | casimps1 | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/casimps1/29168 |
| d41 | E Love | texasradiofish | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/texasradiofish/30157 |
| d42 | I Dont Hurt My Love (pseudo-format Mix) | Vidian | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Vidian/27579 |
| d43 | February (Mumblemix) | Incoherent_Mumble_Train | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Incoherent_Mumble_Train/9209 |
| d44 | Time to Take Out the Trash (bill berry mix) | williamberry | Techno | CC BY 2.5 | ja | https://ccmixter.org/files/williamberry/6855 |
| d45 | Song Of Peace | cdk | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/cdk/25949 |
| d46 | Where's the Party? | texasradiofish | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/texasradiofish/34413 |
| d47 | Moonlight | TheDICE | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/TheDICE/43024 |
| d48 | LoveShadow - You Made It Up ^ (cdk Mix) | cdk | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/cdk/46517 |
| d49 | Robot Summer | gurdonark | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/gurdonark/32410 |
| d50 | Dream of Flight (Techno Twang Mix) | spinmeister | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/spinmeister/18931 |
| d51 | I Wonder If God Was Sleeping (Transcendence Edit) | scottaltham | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/scottaltham/18129 |
| d52 | Easy Killer (dogmix) | oldDog | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/oldDog/24807 |
| d53 | Red Carpet Please! | scomber | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/scomber/31092 |
| d54 | gros bon ange_PoP_MiX | wellman | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/wellman/19803 |
| d55 | Rain Falls Soft Acappella | reblaw27 | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/reblaw27/18667 |
| d56 | Famous Last Words (Gulf Mix) | romancito | Pop | Sampling+ 1.0 | nein | https://ccmixter.org/files/romancito/27528 |
| d57 | My Reflection | wellman | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/wellman/27361 |
| d58 | What Do You Say | mykleanthony | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/mykleanthony/29154 |
| d59 | Touch | jacindae | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/jacindae/19783 |
| d60 | Silver On Fire ( feat. Evguenia Ratsouk ) | fanisatt | Pop | unklar | nein | https://freemusicarchive.org/music/fanisatt//silver-on-fire-feat-evguenia-ratsouk-1/ |
| d61 | RF15 - A Chance For Us All | legacyAlli | Pop | CC BY 4.0 | ja | https://freemusicarchive.org/music/legacyalli/2023-royalty-free-by-legacyalli/rf15-a-chance-for-us-all/ |
| d62 | Damn Good Trouble | NoKings XXX | Pop | CC BY 4.0 | ja | https://freemusicarchive.org/music/nokings-xxx/protest-chongs-jkl/damn-good-trouble/ |
| d63 | Anxiety Creep.mp3 | Mello C | Pop | CC BY-NC 4.0 | nein | https://freemusicarchive.org/music/mello-c/the-situation-is-not-ok/anxiety-creepmp3/ |
| d64 | Flashing Lights | Niva | Pop | unklar | nein | https://freemusicarchive.org/music/niva//flashing-lights/ |
| d65 | Cold Hands, Warm Lies | Wren West | Pop | CC BY-NC 4.0 | nein | https://freemusicarchive.org/music/carlos-javed-1/alone-with-echoes/cold-hands-warm-lies/ |
| d66 | Dance Pop Music | PulseBox | Pop | CC BY-ND 4.0 | nein | https://freemusicarchive.org/music/pulsebox/pop-music-2/dance-pop-music/ |
| d67 | nastoyashiymujig_mastered | CODESCORN | Pop | CC BY-NC 4.0 | nein | https://freemusicarchive.org/music/codescorn/love-1/nastoyashiymujig-mastered/ |
| d68 | Motivational Inspiring Romantic Rock "Moonspire" | Vlad Annenkov | Rock | unklar | nein | https://freemusicarchive.org/music/vlad-annenkov//motivational-inspiring-romantic-rock-moonspire/ |
| d69 | Locking Eyes with Ninki Nanka | Fields Ohio | Rock | CC BY-NC-ND 4.0 | nein | https://freemusicarchive.org/music/Fields_of_Ohio/some-melodies-are-memories-you-never-really-leave-behind-vol-1/locking-eyes-with-ninki-nanka/ |
| d70 | Catechism | bent wyre | Rock | CC BY-ND 4.0 | nein | https://freemusicarchive.org/music/bent-wyre/me-my-shadow/catechism/ |
| d71 | Heavy Pizza Jam | Ə | Rock | CC BY 4.0 | ja | https://freemusicarchive.org/music/e/heavy-pizza-jams/heavy-pizza-jam/ |
| d72 | Chyba je vo mne | Space Recorder | Rock | CC BY-SA 4.0 | nein | https://freemusicarchive.org/music/space-recorder/chyba-je-vo-mne/chyba-je-vo-mne/ |
| d73 | Another Day In The Life | Tommaso Croce | Rock | unklar | nein | https://freemusicarchive.org/music/Dilating_Times//another-day-in-the-life/ |
| d74 | Stand Up Today | Lopkerjo | Rock | CC BY 4.0 | ja | https://freemusicarchive.org/music/john-lopker/alter-of-deception/stand-up-today/ |
| d75 | This Is My Day | Sean Ferree | Rock | unklar | nein | https://freemusicarchive.org/music/sean-ferree//this-is-my-day/ |
| d76 | Klatch | CXR ATK | Techno | CC0 1.0 | ja | https://freemusicarchive.org/music/cxr-atk/klatch/klatch/ |
| d77 | Megabus to Pluto | Ergo Phizmiz | Techno | CC BY-NC-SA 3.0 | nein | https://freemusicarchive.org/music/Ergo_Phizmiz/POPGASMS/06_Megabus-to-Pluto/ |
| d78 | Die | Ninja16 | Techno | CC BY-NC-ND 4.0 | nein | https://freemusicarchive.org/music/Ninja16/Quantum_Jump/06_-_Ninja16_-_Die/ |
| d80 | 001 (or trilobite) | Noisumon | Techno | CC BY-NC-ND 3.0 | nein | https://freemusicarchive.org/music/Noisumon/For_The_Lovers/001_or_trilobite/ |
| d81 | Tramp & Bass (Amam Break) | Pogohm | Techno | CC BY-NC-ND 4.0 | nein | https://freemusicarchive.org/music/Pogohm/Spank/04_-_Pogohm_-_Tramp__Bass_Amam_Break/ |
| d82 | Goddammit, Mark! | Colorcast | Techno | CC BY 4.0 | ja | https://freemusicarchive.org/music/colorcast/from-the-midyear/goddammit-mark/ |
| d83 | Dark Trap / Darkness | Alex-Productions | Techno | unklar | nein | https://freemusicarchive.org/music/alex-productions//dark-trap-darkness/ |
| d84 | Glistener | Oelek | House | CC BY-NC-SA 3.0 | nein | https://freemusicarchive.org/music/Oelek/Some_Kinda_House/Oelek_-_Some_Kinda_House_-_01_Glistener/ |
| d85 | tech | voide | House | CC BY 4.0 | ja | https://freemusicarchive.org/music/voide/betonholle/tech-2/ |
| d86 | SuperDiscoCat | REDCVT | House | CC BY-SA 4.0 | nein | https://freemusicarchive.org/music/redcvt/superdiscocat/superdiscocat/ |
| d87 | Keepsake | JMHBM | House | unklar | nein | https://freemusicarchive.org/music/beat-mekanik//keepsake-1/ |
| d88 | Electronic Abstract | AudioCoffee Band | House | CC BY-NC-ND 4.0 | nein | https://freemusicarchive.org/music/audiocoffee-band/relaxing-dance-waves/electronic-abstract/ |
| d89 | Look What They've Done (To My Song) | The Willing | House | CC BY-NC-SA 3.0 | nein | https://freemusicarchive.org/music/The_Willing/FACETS_1796/Loook_what_Theyve_Done_Mstr_oct26_15_1402/ |
| d90 | Illusion.mp3 | Darkfluxx | House | unklar | nein | https://freemusicarchive.org/music/darkfluxx//illusionmp3/ |
| d91 | Afro House | Alex Morgan | House | CC BY 4.0 | ja | https://freemusicarchive.org/music/alex-morgan/pop-afro-party-instrumentals/afro-house/ |
| d92 | Ich Liebe Dich (Version Mann) | Hasenchat | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2154004 |
| d93 | Die Magie der Liebe | Hasenchat | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2153994 |
| d94 | Die Straßen von Bamberg | Hasenchat | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2153993 |
| d95 | Schlaflos | Frank Schröter | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2283021 |
| d96 | schatten | Schwuchi | Schlager | CC BY-ND 2.0 | nein | https://www.jamendo.com/track/683232 |
| d97 | Herzschlag | Annodomino | Schlager | CC BY-NC-SA 3.0 | nein | https://www.jamendo.com/track/1033890 |
| d98 | Alles nicht so schlimm | Blüx! | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2283128 |
| d99 | Oh Mallorca | Der Schlager Berliner | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2124835 |
| d100 | disco lover (radio mix) | disco retro | House | CC BY 3.0 | ja | https://www.jamendo.com/track/2296943 |
| d101 | La Disco Loca (Radio Edit) | Oleja Kaba | House | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2136701 |
| d102 | Disco Boutique - All I Have (Radio Edit) | Disco Boutique | House | CC BY-NC-ND 2.5 | nein | https://www.jamendo.com/track/788077 |
| d103 | Disco must never Die | Martin Eigenmann | House | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/1845668 |

## Test-Songs (nur Messung, nie gelernt): 56 Songs, davon 0 zum Lernen

Lizenzen: CC BY-NC 3.0 (18), CC BY-NC-ND 3.0 (9), CC BY 4.0 (6), CC BY-NC 4.0 (4), CC BY 3.0 (4), CC BY-NC-SA 3.0 (3), CC BY-SA 3.0 (3), CC BY-NC-ND 2.5 (2), CC BY-NC-SA 4.0 (2), CC BY-NC-ND 4.0 (2), CC0 1.0 (1), NC Sampling+ 1.0 (1), CC BY-ND 2.0 (1)

| ID | Titel | Interpret | Genre | Lizenz | Lernen | Quelle |
|---|---|---|---|---|---|---|
| s01 | Can't Take Our Love Away | Josh Woodward | Pop | CC BY 4.0 | nein | https://freemusicarchive.org/music/Josh_Woodward/Crawford_Street/JoshWoodward-CrawfordStreet-03-CantTakeOurLoveAway/ |
| s02 | Hey Ruth | Josh Woodward | Pop | CC BY 4.0 | nein | https://freemusicarchive.org/music/Josh_Woodward/Crawford_Street/JoshWoodward-CrawfordStreet-10-HeyRuth/ |
| s03 | Tick Tock | Josh Woodward | Pop | CC BY 4.0 | nein | https://freemusicarchive.org/music/Josh_Woodward/The_Beautiful_Machine/JoshWoodward-TBM-07-TickTock/ |
| s04 | Fly | The Kyoto Connection | Pop | CC BY-NC-ND 2.5 | nein | https://freemusicarchive.org/music/The_Kyoto_Connection/The_First_Voyage/02_-_Fly/ |
| s05 | H.E.A.T | The Kyoto Connection | Pop | CC BY-NC-ND 2.5 | nein | https://freemusicarchive.org/music/The_Kyoto_Connection/No_Headphones_Required/05_-_HEAT/ |
| s06 | Two-Timing Lady | Dazie Mae | Pop | CC BY-NC-SA 3.0 | nein | https://freemusicarchive.org/music/Dazie_Mae/Seasonal_Affective_Disorder/01_-_Two-Timing_Lady/ |
| s07 | Crying at the Discoteque | Fhernando | House | CC BY-NC-SA 3.0 | nein | https://freemusicarchive.org/music/Fhernando/none_given_1383/08__Fhernando__Crying_At_The_Discoteque__Last_Days_Of_Disco__FrostWirecom_FrostClickcom_Creative_Common/ |
| s08 | Kiss Me Harder Boy | Fhernando | House | CC BY-NC-SA 3.0 | nein | https://freemusicarchive.org/music/Fhernando/none_given_1383/07__Fhernando__Kiss_Me_Harder_Boy__Last_Days_Of_Disco__FrostWirecom_FrostClickcom_Creative_Common/ |
| s09 | Bad Sign | Brad Sucks | Rock | CC BY-SA 3.0 | nein | https://freemusicarchive.org/music/Brad_Sucks/Out_Of_It/Bad_Sign/ |
| s10 | The Wind Is Rising | Pipe Choir | Rock | CC BY 4.0 | nein | https://freemusicarchive.org/music/Pipe_Choir/Pipe_Choir/The_Wind_Is_Rising/ |
| s11 | Savior | Pipe Choir | Rock | CC BY 4.0 | nein | https://freemusicarchive.org/music/Pipe_Choir/Pipe_Choir/Savior/ |
| s12 | No means no | The Freak Fandango Orchestra | Rock | CC BY-SA 3.0 | nein | https://freemusicarchive.org/music/The_Freak_Fandango_Orchestra/Tales_Of_A_Dead_Fish/No_means_no/ |
| s13 | Late as Usual | The Freak Fandango Orchestra | Rock | CC BY-NC 4.0 | nein | https://freemusicarchive.org/music/The_Freak_Fandango_Orchestra/Tales_Of_A_Dead_Fish/Late_as_Usual_1052/ |
| s14 | Honest | Derek Clegg | Rock | CC BY-NC-SA 4.0 | nein | https://freemusicarchive.org/music/Derek_Clegg/kjc/honest/ |
| s15 | Queue | Nctrnm | House | CC BY-NC 4.0 | nein | https://freemusicarchive.org/music/nctrnm/singles-2/NctrnmQueue125Am/ |
| s16 | Golden | Nctrnm | Techno | CC BY-NC 4.0 | nein | https://freemusicarchive.org/music/nctrnm/singles-2/NctrnmGolden124Cm/ |
| s17 | Unit | Nctrnm | Techno | CC BY-NC 4.0 | nein | https://freemusicarchive.org/music/nctrnm/singles-2/NctrnmUnit125Bbm/ |
| s18 | Shake me, Baby | Soft and Furious | Techno | CC0 1.0 | nein | https://freemusicarchive.org/music/Soft_and_Furious/Dancing_in_the_closet/Soft_and_Furious_-_Dancing_in_the_closet_-_06_Shake_me_Baby/ |
| s19 | Speed Switch | E-Shine | Techno | CC BY-NC-ND 4.0 | nein | https://freemusicarchive.org/music/E-Shine/Variant/05_-_E-Shine_-_Speed_Switch_Bonus/ |
| s20 | Sulphuric Sea | Alien Operator | Techno | CC BY-NC-ND 4.0 | nein | https://freemusicarchive.org/music/Alien_Operator/Acid_Planet_Zuberon/01_-_Alien_Operator_-_Sulphuric_Sea/ |
| s21 | Tropical II | bedocan | Techno | CC BY-NC-SA 4.0 | nein | https://freemusicarchive.org/music/bedocan/storm/tropical-ii/ |
| s22 | Dancing With Lightning | Kellee Maize | Hip-Hop | CC BY 4.0 | nein | https://freemusicarchive.org/music/Kellee_Maize/The_5th_Element/01_Dancing_With_Lightning/ |
| s23 | If You Wait For Me | scottaltham | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/scottaltham/36642 |
| s24 | Uh-oh... | jacindae | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/jacindae/19581 |
| s25 | Both Eyes Closed | stateshirt | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/stateshirt/25911 |
| s26 | Thinkin' About It - Side A | Carosone | Pop | NC Sampling+ 1.0 | nein | https://ccmixter.org/files/Carosone/28935 |
| s27 | Stimmen im Kopf | hansatom | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/hansatom/44795 |
| s28 | Die Liebe Bleibt | texasradiofish | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/texasradiofish/40203 |
| s29 | Sound of my Dreams | One_Project | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/One_Project/45687 |
| s30 | Heartbeat (Jihfa ReMix) | Jihfa | Pop | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Jihfa/44765 |
| s31 | Disappointed | copperhead | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/copperhead/37806 |
| s32 | Death March To Sun Fun City | ruin_roads | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/ruin_roads/22127 |
| s33 | We Are Free | zep_hurme | Rock | CC BY-SA 3.0 | nein | https://ccmixter.org/files/zep_hurme/31607 |
| s34 | And I wish | Loveshadow | Rock | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Loveshadow/52178 |
| s35 | Straight To The Light | AlexBeroza | House | CC BY 3.0 | nein | https://ccmixter.org/files/AlexBeroza/29630 |
| s36 | Love (ft Snowflake) | SackJo22 | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/SackJo22/43979 |
| s37 | SuperSTARS! (w/Vidian and HEJ31) | SackJo22 | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/SackJo22/42137 |
| s38 | CHERRY (Mr N Ms V's Mix) | Vidian | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Vidian/23575 |
| s39 | Apologize (GE Inside Out Remix) | George_Ellinas | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/George_Ellinas/20959 |
| s40 | I Want To Shout Out (7OOP3D Shouda Louda Mix) | 7OOP3D | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/7OOP3D/22020 |
| s41 | The Sweetest Sin | Loveshadow | House | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Loveshadow/14582 |
| s42 | Art Now | AlexBeroza | Techno | CC BY 3.0 | nein | https://ccmixter.org/files/AlexBeroza/30344 |
| s43 | The New Music | AlexBeroza | Techno | CC BY 3.0 | nein | https://ccmixter.org/files/AlexBeroza/30513 |
| s44 | K-I-S-S-I-N-G | Sassygal | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Sassygal/29130 |
| s45 | CCmixter.org | Mixro | Techno | CC BY-NC 3.0 | nein | https://ccmixter.org/files/Mixro/16474 |
| s46 | Turning Into Normal (What Once Felt Strange) | SackJo22 | Techno | CC BY 3.0 | nein | https://ccmixter.org/files/SackJo22/43036 |
| s47 | Campingzeit | Der Schlager Berliner | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2062718 |
| s48 | Oh Mallorca | Der Schlager Berliner | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2062351 |
| s49 | ES ist Liebe | Der Schlager Berliner | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2065653 |
| s50 | Halt Mich Fest | Hasenchat | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2153998 |
| s51 | Tanz in Bayern | Hasenchat | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2153999 |
| s52 | Diese Nacht gehört uns allen - Discofox Popschlager Mix | DJ Brösel | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2346425 |
| s53 | du bist mein Sonnenschein | Schwuchi | Schlager | CC BY-ND 2.0 | nein | https://www.jamendo.com/track/683184 |
| s54 | Ein Hauch von Frühling | Hasenchat | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2154000 |
| s55 | Tanz Mit Mir | Hasenchat | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2153996 |
| s56 | Meine Insel | Hasenchat | Schlager | CC BY-NC-ND 3.0 | nein | https://www.jamendo.com/track/2153995 |

