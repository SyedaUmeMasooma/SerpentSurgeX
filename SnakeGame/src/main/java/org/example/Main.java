package org.example;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.animation.PauseTransition;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import java.awt.Point;
import javafx.util.Duration;
import java.util.*;
import java.io.*;
import java.nio.file.*;


public class Main extends Application {

    //score storage paths
    private static final Path SAVE_DIR  = Paths.get(System.getProperty("user.home"), ".serpent_surge_x");
    private static final Path SAVE_FILE = SAVE_DIR.resolve("data1.txt");

    private static int SCREEN_W;
    private static int SCREEN_H;
    private static int COLS;
    private static int ROWS;
    private static int SQ;
    private static final int RIGHT=0, LEFT=1, UP=2, DOWN=3;
    private static final String COMPANY="⚡ VENOM STUDIOS";
    private static final String GAME_NAME="SERPENT SURGE X";

    private Label hudScoreLabel, hudBestLabel, hudLevelLabel, hudHealthLabel, hudKeysLabel;

    //theme and skin def
    private static final String[][] BACKGROUNDS={
            {"Classic Turf","#ABDE79","#A2D76E"}, {"Midnight Void","#0d0d2b","#0a0a20"}, {"Desert Dunes","#e8c882","#d4b56a"},
            {"Toxic Waste","#1a2f1a","#162614"}, {"Ocean Depths","#0e3460","#0b2a4a"}, {"Lava Field","#3d0000","#2a0000"}};

    private static final String[][] SNAKE_SKINS={
            {"Cobalt Classic","#003566","#001d3d"}, {"Venom Green","#00c853","#007b39"}, {"Blood Red","#b71c1c","#7f0000"},
            {"Galactic Purple","#6a1b9a","#38006b"}, {"Solar Flare","#e65100","#bf360c"}, {"Arctic Ice","#00bcd4","#006064"}};

    //fruit img paths
    private static final String[] FOOD_PATHS={
            "/img/orange.png", "/img/apple.png", "/img/cherry.png", "/img/berry.png", "/img/coconut.png",
            "/img/peach.png", "/img/watermelon.png", "/img/pomegranate.png", "/img/tomato.png"};

    //unlock thresholds
    private static final int[] BG_UNLOCK={0,100,200,350,500,750};
    private static final int[] SKIN_UNLOCK={0,75,150,300,450,700};

    //game state
    private GraphicsContext gc;
    private Timeline timeline;
    private Stage primaryStage;
    private Scene menuScene, gameScene;

    //snake
    private List<Point> snakeBody=new ArrayList<>();
    private Point snakeHead;
    private int currentDirection=RIGHT;
    private int nextDirection=RIGHT;

    //food
    private List<FoodItem> foods=new ArrayList<>();
    private int maxFoods=3;

    private Image[] foodImages;
    private Image superFoodImage;

    //keys and hearts
    private Image keyImage;
    private Image heartImage;
    private List<Point> keys=new ArrayList<>();
    private List<Point> healthItems=new ArrayList<>();
    private int playerKeys=0;
    private int health=3;

    private List<Point> obstacles=new ArrayList<>(); //obstacles

    //enemy snake
    private List<EnemySnake> enemies=new ArrayList<>();
    private Timeline enemyTimeline;

    //score and speed
    private int score=0;
    private int highScore=0;
    private int level=1;
    private int baseSpeed=190;
    private int speed=190;
    private List<ScoreEntry> scoreboard=new ArrayList<>();
    //state flags
    private boolean gameOver=false;
    private boolean paused=false;

    //title animation
    private double titleAlpha=0;
    private boolean titleFading=false;
    private Timeline titleTimeline;

    //player profile
    private String playerName="Player";
    private int selectedBgIndex=0;
    private int selectedSkinIndex=0;
    private String selectedDifficulty="Medium";
    private int totalKeysEver=0;

    private final Random rng=new Random();

    @Override
    public void start(Stage stage)
    {
        this.primaryStage=stage;

        javafx.geometry.Rectangle2D bounds=Screen.getPrimary().getVisualBounds();
        SCREEN_W=(int)bounds.getWidth();
        SCREEN_H=(int)bounds.getHeight();
        COLS=30;
        ROWS=25;
        int HUD_H=50;
        SQ=Math.min(SCREEN_W/COLS,(SCREEN_H-HUD_H)/ROWS);

        loadData();
        showLoadingScreen();
        stage.setTitle(COMPANY + "  │  " + GAME_NAME);
        stage.setResizable(false);
        stage.setMaximized(true);

        stage.show();
    }

    //TOTAL CANVAS PIXEL WIDTH/HEIGHT
    private int canvasW() {return COLS * SQ;}
    private int canvasH() {return ROWS * SQ;}

    //SAVE AND LOAD DATA METHODS FILE I/O
    private void saveData()
    {
        try {
            Files.createDirectories(SAVE_DIR);
            StringBuilder sb=new StringBuilder();

            sb.append("highScore=").append(highScore).append("\n");

            for (ScoreEntry e: scoreboard)
            {
                sb.append(e.name).append(",").append(e.score).append(",").append(e.level).append(",").append(e.keys).append("\n");
            }
            Files.writeString(SAVE_FILE, sb.toString());

            } catch (IOException ex) {
                System.err.println("Save failed: "+ex.getMessage());
            }
    }

    private void loadData()
    {
        if (!Files.exists(SAVE_FILE)) return;
        try {
            List<String> lines=Files.readAllLines(SAVE_FILE);
            if (lines.isEmpty()) return;

            String firstLine=lines.get(0).trim();
            if (firstLine.startsWith("highScore=")) {
                try {
                    highScore=Integer.parseInt(firstLine.substring("highScore=".length()));
                } catch (NumberFormatException e) {
                    highScore=0;
                }
            }

            scoreboard.clear();
            for (int i=1; i<lines.size(); i++) {
                String line=lines.get(i).trim();
                if (line.isBlank()) continue;

                String[] parts= line.split(",");
                if (parts.length>=4) {
                    String name=parts[0];
                    int score=parseSafeInt(parts[1],0);
                    int level=parseSafeInt(parts[2],1);
                    int keys =parseSafeInt(parts[3],0);
                    scoreboard.add(new ScoreEntry(name,score,level,keys));
                }
            }
        } catch (Exception ex) {
            System.err.println("Load failed (starting fresh): " + ex.getMessage());
            scoreboard.clear();
            highScore = 0;
        }
    }
    private int parseSafeInt(String s, int def)
    {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    //LOADING SCREEN
    private void showLoadingScreen()
    {
        StackPane root=new StackPane();
        root.setPrefSize(SCREEN_W, SCREEN_H);

        Image logo=new Image(getClass().getResource("/img/snakelogo.png").toExternalForm());
        ImageView logoView=new ImageView(logo);
        logoView.setFitWidth(SCREEN_W);
        logoView.setFitHeight(SCREEN_H);
        logoView.setPreserveRatio(false);

        Label loading=new Label("Loading...");
        loading.setTextFill(Color.WHITE);
        loading.setFont(Font.font("Impact", 56));
        loading.setMaxWidth(Double.MAX_VALUE);
        loading.setAlignment(Pos.CENTER);

        StackPane.setAlignment(loading, Pos.BOTTOM_CENTER);
        StackPane.setMargin(loading, new Insets(0, 0, 30, 0));

        root.getChildren().addAll(logoView, loading);

        FadeTransition blink= new FadeTransition(Duration.seconds(0.6), loading);
        blink.setFromValue(1.0); blink.setToValue(0.0);
        blink.setCycleCount(FadeTransition.INDEFINITE);
        blink.setAutoReverse(true);
        blink.play();

        Scene loadingScene= new Scene(root, SCREEN_W, SCREEN_H);
        primaryStage.setScene(loadingScene);

        FadeTransition fadeIn= new FadeTransition(Duration.seconds(2), logoView);
        fadeIn.setFromValue(0.0); fadeIn.setToValue(1.0);
        fadeIn.play();

        PauseTransition delay1= new PauseTransition(Duration.seconds(4));
        delay1.setOnFinished(e -> showMenu());
        delay1.play();
    }

    //MENU SCREEN
    private void showMenu() {

        StackPane root = new StackPane();
        root.setPrefSize(SCREEN_W, SCREEN_H);

        Image bgImage= new Image(getClass().getResourceAsStream("/img/menu_bg.png"));
        ImageView bgView= new ImageView(bgImage);
        bgView.setFitWidth(SCREEN_W);
        bgView.setFitHeight(SCREEN_H);
        bgView.setPreserveRatio(false);

        root.getChildren().add(bgView);

        VBox center= new VBox(18);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(40));
        center.setMaxWidth(600);

        Label bestLabel=new Label("\n\n\n\n\n\n\n🏆 All-Time Best: " + highScore);
        bestLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 15));
        bestLabel.setTextFill(Color.BLACK);

        HBox nameRow = styledRow("👤  Player Name:");
        TextField nameField = new TextField(playerName);
        nameField.setStyle(inputStyle());
        nameField.setMaxWidth(200);
        nameField.textProperty().addListener((o, ov, nv) -> playerName = nv.isBlank() ? "Player" : nv);
        nameRow.getChildren().add(nameField);

        HBox diffRow= styledRow("🎯  Difficulty:");
        ComboBox<String> diffBox= new ComboBox<>(FXCollections.observableArrayList("Easy", "Medium", "Hard"));
        diffBox.setValue(selectedDifficulty);
        diffBox.setStyle(comboStyle());
        diffBox.setOnAction(e -> selectedDifficulty= diffBox.getValue());
        diffRow.getChildren().add(diffBox);

        HBox bgRow= styledRow("🌿  Background:");
        ComboBox<String> bgBox=new ComboBox<>();
        for (int i=0; i<BACKGROUNDS.length; i++)
            bgBox.getItems().add(BACKGROUNDS[i][0]+(highScore >= BG_UNLOCK[i] ? "" : "  🔒 " + BG_UNLOCK[i] + "pts"));
        bgBox.setStyle(comboStyle());
        bgBox.getSelectionModel().select(selectedBgIndex);
        bgBox.setOnAction(e -> {
            int idx = bgBox.getSelectionModel().getSelectedIndex();
            if (highScore >= BG_UNLOCK[idx]) selectedBgIndex = idx;
            else { bgBox.getSelectionModel().select(selectedBgIndex); showAlert("🔒 Locked!", "Reach " + BG_UNLOCK[idx] + " pts to unlock."); }
        });
        bgRow.getChildren().add(bgBox);

        HBox skinRow= styledRow("🐍  Snake Skin:");
        ComboBox<String> skinBox= new ComboBox<>();
        for (int i=0; i<SNAKE_SKINS.length; i++)
            skinBox.getItems().add(SNAKE_SKINS[i][0]+(highScore>=SKIN_UNLOCK[i] ? "" : "  🔒 " + SKIN_UNLOCK[i] + "pts"));
        skinBox.setStyle(comboStyle());
        skinBox.getSelectionModel().select(selectedSkinIndex);
        skinBox.setOnAction(e -> {
            int idx= skinBox.getSelectionModel().getSelectedIndex();
            if (highScore>=SKIN_UNLOCK[idx]) selectedSkinIndex=idx;
            else { skinBox.getSelectionModel().select(selectedSkinIndex); showAlert("🔒 Locked!", "Reach "+SKIN_UNLOCK[idx]+" pts to unlock."); }
        });
        skinRow.getChildren().add(skinBox);

        Button playBtn=menuButton("▶  PLAY","#004546","#f0ff87");
        Button howBtn=menuButton("❓  HOW TO PLAY","#004546","#f0ff87");
        Button scoreboardBtn=menuButton("🏆  SCOREBOARD","#004546","#f0ff87");
        Button exitBtn=menuButton("✖  EXIT","#004546","#f0ff87");

        playBtn.setOnAction(e -> startGame());
        howBtn.setOnAction(e -> showHowToPlay());
        scoreboardBtn.setOnAction(e -> showScoreboard());
        exitBtn.setOnAction(e -> Platform.exit());

        center.getChildren().addAll(bestLabel, separator(), nameRow, diffRow, bgRow, skinRow, separator(), playBtn, howBtn, scoreboardBtn, exitBtn);

        FadeTransition ft=new FadeTransition(Duration.seconds(1.2), center);
        ft.setFromValue(0); ft.setToValue(1); ft.play();

        root.getChildren().add(center);
        menuScene= new Scene(root, SCREEN_W, SCREEN_H);
        primaryStage.setScene(menuScene);
    }

    //HOW TO PLAY INSTRUCTIONS
    private void showHowToPlay() {
        Alert a= new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("How To Play — "+GAME_NAME);
        a.setHeaderText("🐍  "+GAME_NAME+"  ─  Controls & Rules");
        a.setContentText(
                "MOVEMENT\n"+"  Arrow Keys or W/A/S/D  →  Move snake\n\n" +
                "ITEMS\n"+
                "  🍎 Fruits             +5 pts   (normal)\n"+
                "  ⭐ Super Fruit    +25 pts  (rare, blinks)\n"+
                "  🔑 Key               +1 key\n"+
                "  ❤  Heart            +1 HP\n\n"+
                "GAME RULES\n" +
                "  • Eat fruits to grow and score.\n"+
                "  • Hit a wall or your body → lose 1 HP.\n"+
                "  • Every 50 pts → new level, faster speed.\n"+
                "  • Hard mode adds obstacles & enemy snakes!\n\n"+
                "KEYS IN-GAME\n"+
                "  P  →  Pause / Resume\n"+
                "  R  →  Restart  (game over screen)\n"+
                "  M  →  Return to Menu");
        a.showAndWait();
    }

    //SCOREBOARD
    private void showScoreboard() {
        Stage s= new Stage();
        s.setTitle("🏆 Scoreboard");
        VBox box= new VBox(12);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color:#0d0d2b;");
        box.setAlignment(Pos.CENTER);

        Label hdr= new Label("🏆  HALL OF FAME");
        hdr.setFont(Font.font("Impact", 28));
        hdr.setTextFill(Color.GOLD);
        box.getChildren().addAll(hdr, separator());

        if (scoreboard.isEmpty()) {
            Label empty=new Label("No scores yet. Go play!");
            empty.setTextFill(Color.web("#aaa"));
            box.getChildren().add(empty);
        } else {
            box.getChildren().add(scoreRow("RANK", "PLAYER", "SCORE", "LEVEL", "KEYS", true));
            int rank=1;
            for (ScoreEntry e : scoreboard)
                box.getChildren().add(scoreRow(rank++ + ".", e.name, String.valueOf(e.score), String.valueOf(e.level), String.valueOf(e.keys), false));
        }

        box.getChildren().add(separator());
        Label session=new Label("All-Time Best: "+highScore+"  │  Total Keys: "+totalKeysEver);
        session.setTextFill(Color.web("#00ffe7"));
        session.setFont(Font.font("Courier New", 14));
        box.getChildren().add(session);

        Label savedNote=new Label("✅ Scores are saved permanently across sessions.");
        savedNote.setTextFill(Color.web("#88ff88"));
        savedNote.setFont(Font.font("Courier New", 12));
        box.getChildren().add(savedNote);

        Button close = menuButton("Close", "#ff5555", "#330000");
        close.setOnAction(e2 -> s.close());
        box.getChildren().add(close);

        s.setScene(new Scene(box, 560, 500));
        s.show();
    }

    //GAME INITIALIZATION
    private void startGame()
    {
        stopAll();

        switch (selectedDifficulty) {
            case "Easy":baseSpeed= 240; maxFoods= 6; break;
            case "Hard":baseSpeed= 160; maxFoods= 8; break;
            default:baseSpeed= 200; maxFoods= 7; break;  // Medium
        }
        speed= baseSpeed;
        score= 0; level= 1; health= 3; playerKeys= 0;
        currentDirection= RIGHT; nextDirection= RIGHT;
        gameOver= false; paused= false;
        titleAlpha= 1.0; titleFading= false;

        snakeBody.clear(); foods.clear(); keys.clear(); healthItems.clear(); obstacles.clear(); enemies.clear();

        Canvas canvas = new Canvas(canvasW(), canvasH());
        gc = canvas.getGraphicsContext2D();

        if (foodImages==null) {
            foodImages=Arrays.stream(FOOD_PATHS).map(p -> new Image(getClass().getResourceAsStream(p))).toArray(Image[]::new);
            superFoodImage=new Image(getClass().getResourceAsStream("/img/super1.png"));
            keyImage=new Image(getClass().getResourceAsStream("/img/key.png"));
            heartImage=new Image(getClass().getResourceAsStream("/img/heart.png"));
        }

        HBox hud= new HBox(40);
        hud.setAlignment(Pos.CENTER);
        hud.setPadding(new Insets(8, 20, 8, 20));
        hud.setStyle("-fx-background-color: black;");

        Label scoreLabel=new Label("Score: "+score);
        Label bestLabel=new Label("Best: "+highScore);
        Label levelLabel=new Label("Lv "+level);
        Label healthLabel=new Label("❤ ❤ ❤");
        Label keysLabel=new Label("🔑 "+playerKeys);
        Label hintLabel=new Label("P=Pause  M=Menu");

        String hudFont="-fx-font-family:'Courier New'; -fx-font-weight:bold; -fx-font-size:15;";
        scoreLabel.setStyle(hudFont+"-fx-text-fill:#00ffe7;");
        bestLabel.setStyle(hudFont+"-fx-text-fill:#00ffe7;");
        levelLabel.setStyle(hudFont+"-fx-text-fill:#00ffe7;");
        healthLabel.setStyle(hudFont+"-fx-text-fill:hotpink;");
        keysLabel.setStyle(hudFont+"-fx-text-fill:gold;");
        hintLabel.setStyle(hudFont+"-fx-text-fill:#888888; -fx-font-weight:normal;");

        hud.getChildren().addAll(scoreLabel,bestLabel,levelLabel,healthLabel,keysLabel,hintLabel);

        this.hudScoreLabel=scoreLabel;
        this.hudBestLabel=bestLabel;
        this.hudLevelLabel=levelLabel;
        this.hudHealthLabel=healthLabel;
        this.hudKeysLabel=keysLabel;

        BorderPane root=new BorderPane();
        root.setTop(hud);

        StackPane canvasWrapper= new StackPane(new Group(canvas));
        canvasWrapper.setAlignment(Pos.TOP_CENTER);
        canvasWrapper.setStyle("-fx-background-color:black;");
        root.setCenter(canvasWrapper);

        root.setStyle("-fx-background-color:black;");

        gameScene= new Scene(root, SCREEN_W, SCREEN_H);
        primaryStage.setScene(gameScene); primaryStage.setMaximized(true);

        gameScene.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case RIGHT: case D: if (currentDirection != LEFT)  nextDirection=RIGHT; break;
                case LEFT: case A: if (currentDirection != RIGHT)  nextDirection=LEFT; break;
                case UP: case W: if (currentDirection != DOWN)  nextDirection=UP; break;
                case DOWN: case S: if (currentDirection != UP)  nextDirection=DOWN; break;
                case P: paused = !paused; break;
                case R: if (gameOver) { stopAll();  startGame(); break;}
                case M: stopAll(); showMenu(); break;
                default: break;
            }}
        );
        initSnake();
        generateObstacles();
        spawnFoods();
        spawnKeys();
        if ("Hard".equals(selectedDifficulty)) spawnEnemies();

        startInGameTitleFade();
        startGameLoop();
    }

    private void initSnake() {
        int startX= COLS/4;
        int startY= ROWS/2;
        for (int i=0; i<3; i++) snakeBody.add(new Point(startX - i, startY));
        snakeHead = snakeBody.get(0);
    }

    //GAME LOOP
    private void startGameLoop() {
        if (timeline!=null) { timeline.stop(); timeline = null; }
        timeline= new Timeline(new KeyFrame(Duration.millis(speed), e -> run()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void run() {
        if (paused) { drawPause(); return; }
        if (gameOver) { drawGameOver(); return; }

        currentDirection=nextDirection;
        drawBackground();
        drawObstacles();
        moveSnake();
        checkCollisions();
        eatFood();
        pickupItems();
        drawFoods();
        drawPickups();
        drawEnemies();
        drawSnake();
        drawHUD();
        if (titleAlpha>0) drawInGameTitle();
    }


    //DRAW METHODS
    private void drawBackground() {
        String c1=BACKGROUNDS[selectedBgIndex][1];
        String c2=BACKGROUNDS[selectedBgIndex][2];
        for (int row=0; row<ROWS; row++) {
            for (int col=0; col<COLS; col++) {
                gc.setFill((row+col)%2 == 0 ? Color.web(c1) : Color.web(c2));
                gc.fillRect(col*SQ, row*SQ, SQ, SQ);
            }
        }
    }

    private void drawObstacles() {
        for (Point p : obstacles) {
            gc.setFill(Color.web("#444444"));
            gc.fillRect(p.x*SQ, p.y*SQ, SQ, SQ);
            gc.setFill(Color.web("#666666"));
            gc.fillRect(p.x*SQ+2, p.y*SQ+2, SQ-4, SQ-4);
        }
    }

    private void drawFoods() {
        for (FoodItem f : foods) {
            if (f.x<0 || f.x>=COLS || f.y<0 || f.y>=ROWS) continue;

            if (f.isSuper) {
                long tick = System.currentTimeMillis() / 300;
                gc.setGlobalAlpha(tick%2 == 0 ? 1.0 : 0.55);
                gc.drawImage(superFoodImage, f.x*SQ, f.y*SQ, SQ, SQ);
                gc.setGlobalAlpha(1.0);
            } else {
                gc.drawImage(foodImages[f.imageIndex], f.x*SQ, f.y*SQ, SQ, SQ);
            }
        }
    }

    private void drawPickups() {
        for (Point k : keys) {
            if (k.x<0 || k.x>=COLS || k.y<0 || k.y>=ROWS) continue;
            gc.drawImage(keyImage, k.x*SQ, k.y*SQ, SQ, SQ);
        }
        for (Point h : healthItems) {
            if (h.x<0 || h.x>=COLS || h.y<0 || h.y>=ROWS) continue;
            gc.drawImage(heartImage, h.x*SQ, h.y*SQ, SQ, SQ);
        }
    }

    private void drawSnake() {
        String headHex = SNAKE_SKINS[selectedSkinIndex][1];
        String bodyHex = SNAKE_SKINS[selectedSkinIndex][2];

        gc.setFill(Color.web(bodyHex));
        for (int i= 1; i< snakeBody.size(); i++) {
            Point p = snakeBody.get(i);
            gc.fillRoundRect(p.x*SQ+1, p.y*SQ+1, SQ-2, SQ-2, 12, 12);
        }

        gc.setFill(Color.web(headHex));
        gc.fillRoundRect(snakeHead.x * SQ, snakeHead.y * SQ, SQ - 1, SQ - 1, 18, 18);

        int ex1, ey1, ex2, ey2;
        switch (currentDirection) {
            case RIGHT: ex1 = SQ - 8;  ey1 = 5;  ex2 = SQ- 8; ey2 = SQ- 9; break;
            case LEFT:  ex1 = 3;  ey1 = 5;  ex2 = 3;  ey2 = SQ- 9; break;
            case UP:    ex1 = 5;  ey1 = 3;  ex2 = SQ- 9; ey2 = 3;  break;
            default:    ex1 = 5;  ey1 = SQ- 8;  ex2 = SQ- 9; ey2 = SQ- 8; break;
        }
        gc.setFill(Color.WHITE);
        gc.fillOval(snakeHead.x * SQ + ex1, snakeHead.y * SQ + ey1, 5, 5);
        gc.fillOval(snakeHead.x * SQ + ex2, snakeHead.y * SQ + ey2, 5, 5);
        gc.setFill(Color.BLACK);
        gc.fillOval(snakeHead.x * SQ + ex1 + 1, snakeHead.y * SQ + ey1 + 1, 3, 3);
        gc.fillOval(snakeHead.x * SQ + ex2 + 1, snakeHead.y * SQ + ey2 + 1, 3, 3);
    }

    private void drawEnemies() {
        for (EnemySnake en : enemies) {
            gc.setFill(Color.web("#8b0000"));
            for (int i = 1; i < en.body.size(); i++) {
                Point p = en.body.get(i);
                gc.fillRoundRect(p.x * SQ + 2, p.y * SQ + 2, SQ - 4, SQ - 4, 10, 10);
            }
            if (!en.body.isEmpty()) {
                gc.setFill(Color.DARKRED);
                Point h = en.body.get(0);
                gc.fillRoundRect(h.x * SQ, h.y * SQ, SQ - 1, SQ - 1, 16, 16);
            }
        }
    }

    private void drawHUD() {
        if (hudScoreLabel == null) return;
        hudScoreLabel.setText("Score: " + score);
        hudBestLabel.setText("Best: " + highScore);
        hudLevelLabel.setText("Lv " + level);
        hudKeysLabel.setText("🔑 " + playerKeys);

        StringBuilder hearts= new StringBuilder();
        for (int i = 0; i < health; i++) hearts.append("❤ ");
        hudHealthLabel.setText(hearts.toString());
    }

    private void drawPause() {
        gc.setFill(Color.color(0, 0, 0, 0.65));
        gc.fillRect(0, 0, canvasW(), canvasH());

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.YELLOW);
        gc.setFont(Font.font("Impact", 72));
        gc.fillText("PAUSED", canvasW() / 2.0, canvasH() / 2.0);

        gc.setFill(Color.web("#aaaaaa"));
        gc.setFont(Font.font("Courier New", 20));
        gc.fillText("Press P to continue", canvasW() / 2.0, canvasH() / 2.0 + 50);
    }

    private void drawGameOver() {
        gc.setFill(Color.color(0, 0, 0, 0.75));
        gc.fillRect(0, 0, canvasW(), canvasH());

        gc.setTextAlign(TextAlignment.CENTER);

        gc.setFill(Color.RED);
        gc.setFont(Font.font("Impact", 72));
        gc.fillText("GAME OVER", canvasW() / 2.0, canvasH() / 2.0 - 20);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
        gc.fillText("Score: " + score + "  |  Level: " + level, canvasW() / 2.0, canvasH() / 2.0 + 40);

        gc.setFill(Color.web("#aaaaaa"));
        gc.setFont(Font.font("Courier New", 18));
        gc.fillText("R = Restart     M = Menu", canvasW() / 2.0, canvasH() / 2.0 + 80);
    }

    //IN GAME TITLE FADE
    private void startInGameTitleFade() {
        titleAlpha= 1.0;
        titleFading= false;
        if (titleTimeline != null) titleTimeline.stop();

        titleTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1.5), e -> titleFading = true)
        );
        titleTimeline.setCycleCount(1);
        titleTimeline.setOnFinished(e -> {
            Timeline fadeLoop = new Timeline(new KeyFrame(Duration.millis(50), ev -> {
                if (titleFading && titleAlpha > 0) titleAlpha = Math.max(0, titleAlpha - 0.03);
            }));
            fadeLoop.setCycleCount(Animation.INDEFINITE);
            fadeLoop.play();
            titleTimeline = fadeLoop;
        });
        titleTimeline.play();
    }

    private void drawInGameTitle() {
        gc.save();
        gc.setGlobalAlpha(titleAlpha);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("white"));
        gc.setFont(Font.font("Comic Sans MS", FontWeight.EXTRA_BOLD, 54));
        gc.fillText(GAME_NAME, canvasW() / 2.0, canvasH() / 2.0 - 30);

        gc.setFill(Color.web("red"));
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 18));
        gc.fillText("Difficulty: " + selectedDifficulty, canvasW() / 2.0, canvasH() / 2.0 + 20);

        gc.restore();
    }

    //MOVEMENT AND LOGIC
    private void moveSnake() {
        for (int i = snakeBody.size() - 1; i >= 1; i--) {
            snakeBody.get(i).x = snakeBody.get(i - 1).x;
            snakeBody.get(i).y = snakeBody.get(i - 1).y;
        }
        switch (currentDirection) {
            case RIGHT: snakeHead.x++; break;
            case LEFT: snakeHead.x--; break;
            case UP: snakeHead.y--; break;
            case DOWN: snakeHead.y++; break;
        }
    }

    private void checkCollisions() {
        if (snakeHead.x< 0 || snakeHead.x>= COLS || snakeHead.y< 0 || snakeHead.y>= ROWS) {
            loseHealth(); return;
        }
        for (int i=1; i<snakeBody.size(); i++) {
            if (snakeHead.x == snakeBody.get(i).x && snakeHead.y == snakeBody.get(i).y) {
                loseHealth(); return;
            }
        }
        for (Point o : obstacles) {
            if (snakeHead.x == o.x && snakeHead.y == o.y) { loseHealth(); return; }
        }
        for (EnemySnake en : enemies) {
            for (Point p : en.body) {
                if (snakeHead.x == p.x && snakeHead.y == p.y) { loseHealth(); return; }
            }
        }
    }

    private void loseHealth() {
        health--;
        if (health<=0) {
            gameOver= true;

            if (score > highScore) highScore = score;

            boolean found = false;
            for (ScoreEntry e : scoreboard) {
                if (e.name.equalsIgnoreCase(playerName)) {
                    if (score > e.score) {
                        e.score = score;
                        e.level = level;
                        e.keys  = playerKeys + totalKeysEver;
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                scoreboard.add(new ScoreEntry(playerName, score, level, playerKeys + totalKeysEver));
            }
            scoreboard.sort((a, b) -> b.score - a.score);
            if (scoreboard.size() > 10) scoreboard = new ArrayList<>(scoreboard.subList(0, 10));

            saveData();

        } else {
            snakeBody.clear();
            int startX = COLS / 4;
            int startY = ROWS / 2;
            for (int i = 0; i < 3; i++) snakeBody.add(new Point(startX - i, startY));
            snakeHead = snakeBody.get(0);
            currentDirection = RIGHT; nextDirection = RIGHT;
        }
    }

    private void eatFood() {
        Iterator<FoodItem> it = foods.iterator();
        while (it.hasNext()) {
            FoodItem f = it.next();
            if (snakeHead.x == f.x && snakeHead.y == f.y) {
                snakeBody.add(new Point(-1, -1));
                score += f.isSuper ? 25 : 5;
                if (score > highScore) highScore = score;
                it.remove();
                int newLevel = score / 50 + 1;
                if (newLevel > level) { level = newLevel; increaseSpeed(); }
            }
        }
        while (foods.size() < maxFoods) spawnOneFood();
    }

    private void pickupItems() {
        Iterator<Point> ki = keys.iterator();
        while (ki.hasNext()) {
            Point k = ki.next();
            if (snakeHead.x == k.x && snakeHead.y == k.y) {
                playerKeys++; totalKeysEver++;
                ki.remove();
                new Timeline(new KeyFrame(Duration.seconds(8), e -> spawnOneKey())).play();
            }
        }
        Iterator<Point> hi = healthItems.iterator();
        while (hi.hasNext()) {
            Point h = hi.next();
            if (snakeHead.x == h.x && snakeHead.y == h.y) {
                health = Math.min(health + 1, 5);
                hi.remove();
                new Timeline(new KeyFrame(Duration.seconds(12), e -> spawnHealthItem())).play();
            }
        }
    }

    private void increaseSpeed() {
        final int STEP = 8;
        int minSpeed;
        switch (selectedDifficulty) {
            case "Easy":  minSpeed = 160; break;
            case "Hard":  minSpeed =  80; break;
            default:      minSpeed = 110; break;  // Medium
        }
        speed = Math.max(minSpeed, baseSpeed - (level - 1) * STEP);
        startGameLoop();
    }

    //SPAWNING
    private void spawnFoods() {
        for (int i = 0; i < maxFoods; i++) spawnOneFood();
    }

    private void spawnOneFood() {
        Point p = freeCell();
        if (p == null) return;
        foods.add(new FoodItem(p.x, p.y, rng.nextInt(6) == 0, rng.nextInt(FOOD_PATHS.length)));
    }

    private void spawnKeys() {
        spawnOneKey();
        spawnHealthItem();
    }

    private void spawnOneKey() {
        Point p = freeCell();
        if (p != null) keys.add(p);
    }

    private void spawnHealthItem() {
        Point p = freeCell();
        if (p != null) healthItems.add(p);
    }

    private void generateObstacles() {
        obstacles.clear();
        if ("Easy".equals(selectedDifficulty)) return;
        int count = "Medium".equals(selectedDifficulty) ? 6 : 14;
        for (int i = 0; i < count; i++) {
            Point p = freeCellExcludeCenter();
            if (p != null) obstacles.add(p);
        }
    }

    private void spawnEnemies() {
        for (int n = 0; n < 2; n++) {
            Point p = freeCell();
            if (p == null) continue;
            EnemySnake en = new EnemySnake();
            for (int i = 0; i < 3; i++) en.body.add(new Point(p.x - i, p.y));
            enemies.add(en);
        }
        enemyTimeline = new Timeline(new KeyFrame(Duration.millis(350), e -> moveEnemies()));
        enemyTimeline.setCycleCount(Animation.INDEFINITE);
        enemyTimeline.play();
    }

    private void moveEnemies() {
        if (gameOver || paused) return;
        for (EnemySnake en : enemies) {
            if (en.body.isEmpty()) continue;
            Point head = en.body.get(0);
            int dx= snakeHead.x-head.x;
            int dy= snakeHead.y-head.y;
            int nx= head.x+(Math.abs(dx)>=Math.abs(dy) ? (dx > 0 ? 1 : -1) : 0);
            int ny= head.y+(Math.abs(dy)>Math.abs(dx) ? (dy > 0 ? 1 : -1) : 0);
            nx= Math.max(0, Math.min(COLS - 1, nx));
            ny= Math.max(0, Math.min(ROWS - 1, ny));
            for (int i = en.body.size() - 1; i >= 1; i--) {
                en.body.get(i).x= en.body.get(i-1).x;
                en.body.get(i).y= en.body.get(i-1).y;
            }
            en.body.get(0).setLocation(nx, ny);
        }
    }

    //FREE CELLS
    private Point freeCell() {
        for (int attempt= 0; attempt< 300; attempt++) {
            int x = rng.nextInt(COLS);
            int y = rng.nextInt(ROWS);
            if (cellFree(x, y)) return new Point(x, y);
        }
        return null;
    }

    private Point freeCellExcludeCenter() {
        for (int attempt= 0; attempt< 300; attempt++) {
            int x = rng.nextInt(COLS);
            int y = rng.nextInt(ROWS);
            if (Math.abs(x - COLS / 2) < 4 && Math.abs(y - ROWS / 2) < 4) continue;
            if (cellFree(x, y)) return new Point(x, y);
        }
        return null;
    }

    private boolean cellFree(int x, int y) {
        for (Point s : snakeBody)   if (s.x == x && s.y == y) return false;
        for (FoodItem f : foods)    if (f.x == x && f.y == y) return false;
        for (Point k : keys)    if (k.x == x && k.y == y) return false;
        for (Point h : healthItems) if (h.x == x && h.y == y) return false;
        for (Point o : obstacles)   if (o.x == x && o.y == y) return false;
        for (EnemySnake en : enemies)
            for (Point p : en.body) if (p.x == x && p.y == y) return false;
        return true;
    }

    private void stopAll() {
        if (timeline!= null) { timeline.stop(); timeline= null; }
        if (enemyTimeline != null) { enemyTimeline.stop(); enemyTimeline = null; }
        if (titleTimeline != null) { titleTimeline.stop(); titleTimeline = null; }
    }

    //MENU HELPERS
    private Button menuButton(String text, String fg, String bg) {
        Button b = new Button(text);
        b.setMaxWidth(320); b.setPrefWidth(320);
        b.setFont(Font.font("Courier New", FontWeight.BOLD, 18));
        String base  = "-fx-background-color:" + bg + "; -fx-text-fill:" + fg + "; -fx-border-color:" + fg + "; -fx-border-width:2; -fx-padding:10 20;" +
                       "-fx-background-radius:6; -fx-border-radius:6; -fx-cursor:hand;";

        String hover = "-fx-background-color:" + fg + "33; -fx-text-fill:" + fg + "; -fx-border-color:" + fg + "; -fx-border-width:2; -fx-padding:10 20;" +
                       "-fx-background-radius:6; -fx-border-radius:6; -fx-cursor:hand;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e  -> b.setStyle(base));
        return b;
    }

    private HBox styledRow(String labelText) {
        HBox h = new HBox(14);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setMaxWidth(460);
        Label l = new Label(labelText);
        l.setFont(Font.font("Courier New",FontWeight.BOLD, 15));

        l.setTextFill(Color.web("black"));
        l.setPrefWidth(170);
        h.getChildren().add(l);
        return h;
    }

    private Separator separator() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:GREEN; -fx-border-color:BLACK;");
        s.setMaxWidth(460);
        return s;
    }

    private String inputStyle() {
        return "-fx-background-color:white; -fx-text-fill:green; -fx-border-color:green;" + "-fx-border-radius:4; -fx-background-radius:4; -fx-font-size:14;";
    }

    private String comboStyle() {
        return "-fx-background-color:WHITE; -fx-text-fill:#00392B; -fx-border-color:green;" + "-fx-border-radius:4; -fx-background-radius:4; -fx-font-size:14;";
    }

    private HBox scoreRow(String r, String n, String s, String lv, String k, boolean header) {
        HBox h = new HBox(0);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setMaxWidth(500);
        String style = header ? "-fx-text-fill:#ffd700; -fx-font-family:'Courier New'; -fx-font-weight:bold; -fx-font-size:14;"
                              : "-fx-text-fill:white; -fx-font-family:'Courier New'; -fx-font-size:13;";

        for (String[] pair : new String[][]{{r,"50"},{n,"160"},{s,"100"},{lv,"70"},{k,"80"}}) {
            Label lb = new Label(pair[0]);
            lb.setPrefWidth(Double.parseDouble(pair[1]));
            lb.setStyle(style);
            h.getChildren().add(lb);
        }
        return h;
    }

    private void showAlert(String title, String msg) {
        Alert a= new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null);
        a.showAndWait();
    }

    //INNER CLASSES
    static class FoodItem {
        int x, y, imageIndex;
        boolean isSuper;
        FoodItem(int x, int y, boolean isSuper, int imageIndex) {
            this.x = x; this.y = y; this.isSuper = isSuper; this.imageIndex = imageIndex;
        }
    }

    static class EnemySnake {
        List<Point> body=new ArrayList<>();
    }

    static class ScoreEntry {
        String name; int score, level, keys;
        ScoreEntry(String n, int s, int l, int k) { name=n; score=s; level=l; keys=k; }
    }

    public static void main(String[] args) { launch(args); }
}