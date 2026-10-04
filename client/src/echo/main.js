import {boot} from './app.js';
import {GameGateway} from '../integration/game-gateway.js';
boot(new GameGateway());
